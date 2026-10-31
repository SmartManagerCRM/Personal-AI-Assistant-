// Minimal JNI bridge to llama.cpp for the SmartManager local brain (Qwen3 GGUF). One model + one context; each generate() call is stateless
// (the Kotlin side supplies the whole ChatML prompt), so the KV cache is cleared on every call.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include <chrono>
#include "llama.h"

#define TAG "smartllama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
struct Handle { llama_model *model = nullptr; llama_context *ctx = nullptr; const llama_vocab *vocab = nullptr; int nCtx = 0; int nBatch = 512; };
bool g_backend = false;
}

extern "C" JNIEXPORT jlong JNICALL Java_com_smartmanager_ai_ai_NativeLlama_load(JNIEnv *env, jobject, jstring jpath, jint nCtx, jint threads) {
    if (!g_backend) { llama_backend_init(); g_backend = true; }
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) { LOGE("model load failed"); return 0; }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = nCtx; cp.n_batch = 512; cp.n_ubatch = 512; cp.n_threads = threads; cp.n_threads_batch = threads;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); LOGE("context init failed"); return 0; }
    auto *h = new Handle(); h->model = model; h->ctx = ctx; h->vocab = llama_model_get_vocab(model); h->nCtx = nCtx; h->nBatch = 512;
    LOGI("loaded model, n_ctx=%d threads=%d", nCtx, threads);
    return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT void JNICALL Java_com_smartmanager_ai_ai_NativeLlama_free(JNIEnv *, jobject, jlong handle) {
    auto *h = reinterpret_cast<Handle *>(handle); if (!h) return;
    llama_free(h->ctx); llama_model_free(h->model); delete h;
}

// Returns raw UTF-8 bytes (decoded in Kotlin) because JNI NewStringUTF only accepts *modified* UTF-8.
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_smartmanager_ai_ai_NativeLlama_generate(JNIEnv *env, jobject, jlong handle, jstring jprompt, jint maxTokens, jfloat temperature) {
    auto *h = reinterpret_cast<Handle *>(handle);
    std::string out;
    auto finish = [&]() { jbyteArray arr = env->NewByteArray((jsize) out.size()); env->SetByteArrayRegion(arr, 0, (jsize) out.size(), reinterpret_cast<const jbyte *>(out.data())); return arr; };
    if (!h) return finish();

    const char *p = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(p); env->ReleaseStringUTFChars(jprompt, p);

    std::vector<llama_token> toks(prompt.size() + 16);
    int n = llama_tokenize(h->vocab, prompt.c_str(), (int) prompt.size(), toks.data(), (int) toks.size(), /*add_special*/ false, /*parse_special*/ true);
    if (n < 0) { toks.resize(-n); n = llama_tokenize(h->vocab, prompt.c_str(), (int) prompt.size(), toks.data(), (int) toks.size(), false, true); }
    if (n <= 0) return finish();
    toks.resize(n);
    const int room = h->nCtx - maxTokens - 8;
    if (n > room && room > 0) { toks.erase(toks.begin(), toks.begin() + (n - room)); n = room; }   // keep the most recent context

    llama_memory_clear(llama_get_memory(h->ctx), true);
    for (int i = 0; i < n; i += h->nBatch) {
        int cnt = std::min(h->nBatch, n - i);
        llama_batch b = llama_batch_get_one(toks.data() + i, cnt);
        if (llama_decode(h->ctx, b) != 0) { LOGE("prompt decode failed"); return finish(); }
    }

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(h->vocab), 64, 1.15f, 0.0f, 0.0f));   // stops repetition loops
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

    char piece[256];
    for (int i = 0; i < maxTokens; i++) {
        llama_token t = llama_sampler_sample(smpl, h->ctx, -1);
        if (llama_vocab_is_eog(h->vocab, t)) break;
        int len = llama_token_to_piece(h->vocab, t, piece, sizeof(piece), 0, false);
        if (len > 0) out.append(piece, len);
        llama_batch b = llama_batch_get_one(&t, 1);
        if (llama_decode(h->ctx, b) != 0) { LOGE("decode failed"); break; }
    }
    llama_sampler_free(smpl);
    return finish();
}
