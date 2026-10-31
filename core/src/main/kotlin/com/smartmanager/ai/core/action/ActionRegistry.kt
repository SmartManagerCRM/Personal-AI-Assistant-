package com.smartmanager.ai.core.action

import com.smartmanager.ai.core.intent.ActionName
import com.smartmanager.ai.core.intent.ParsedIntent
import com.smartmanager.ai.core.intent.Slots

/** Android permission groups the assistant manages individually. */
enum class AppPermission { MICROPHONE, CONTACTS, PHONE, NOTIFICATIONS, NOTIFICATION_ACCESS, CAMERA, MEDIA, CALENDAR, LOCATION, BLUETOOTH, WRITE_SETTINGS }

/** Level 1 automatic / Level 2 confirmation / Level 3 restricted (never automatic). */
enum class RiskLevel { AUTOMATIC, CONFIRM, RESTRICTED }

enum class ResultStatus {
    /** Operation finished and the outcome was verified. */
    SUCCESS,
    /** Handed off to Android/another app; completion cannot be verified (e.g. dialer opened, WhatsApp draft prepared). */
    INITIATED,
    /** User must complete the final step (press Send, grant a system dialog…). */
    NEEDS_USER_ACTION,
    NOT_FOUND, AMBIGUOUS, UNAVAILABLE, FAILED,
}

data class ActionResult(
    val status: ResultStatus,
    val detail: String = "",
    /** True only when the handler positively verified the outcome (e.g. calendar row read back). */
    val verified: Boolean = false,
    val data: Map<String, String> = emptyMap(),
) {
    val isOk get() = status == ResultStatus.SUCCESS || status == ResultStatus.INITIATED || status == ResultStatus.NEEDS_USER_ACTION
}

/** Entity resolution step run before confirmation so the user hears *who* will be called/messaged. */
sealed interface Preparation {
    data class Ready(val intent: ParsedIntent, val subject: String? = null, val extra: Map<String, String> = emptyMap()) : Preparation
    data class Failed(val result: ActionResult) : Preparation
}

interface ActionHandler {
    suspend fun prepare(intent: ParsedIntent): Preparation = Preparation.Ready(intent)
    suspend fun execute(intent: ParsedIntent): ActionResult
}

/** Definition = name + validated parameters + permissions + risk + confirmation policy + handler. */
data class ActionDefinition(
    val name: ActionName,
    val requiredSlots: Set<String> = emptySet(),
    val permissions: Set<AppPermission> = emptySet(),
    val risk: RiskLevel = RiskLevel.AUTOMATIC,
    val validator: (ParsedIntent) -> String? = { null },
    val handler: ActionHandler,
)

fun interface PermissionChecker { fun isGranted(p: AppPermission): Boolean }

data class AuditEntry(
    val timeMs: Long, val action: String, val risk: RiskLevel, val outcome: String, val summary: String, val confirmed: Boolean,
)

fun interface AuditLog { suspend fun record(entry: AuditEntry) }

sealed interface Outcome {
    data class NeedsSlots(val missing: List<String>) : Outcome
    data class NeedsPermission(val permissions: Set<AppPermission>) : Outcome
    data class NeedsConfirmation(val intent: ParsedIntent, val subject: String?) : Outcome
    data class Refused(val reason: String) : Outcome
    data class Invalid(val reason: String) : Outcome
    data class Executed(val intent: ParsedIntent, val result: ActionResult) : Outcome
}

/**
 * The only way the assistant (rule parser *or* LLM) can touch the phone. Unknown action names, missing
 * parameters, denied permissions and Level-3 actions never reach a handler.
 */
class ActionRegistry(
    private val permissions: PermissionChecker,
    private val audit: AuditLog = AuditLog { },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val defs = LinkedHashMap<ActionName, ActionDefinition>()
    fun register(d: ActionDefinition) { defs[d.name] = d }
    fun definition(n: ActionName) = defs[n]
    fun registered(): Set<ActionName> = defs.keys

    suspend fun run(intent: ParsedIntent, confirmed: Boolean): Outcome {
        val def = defs[intent.action] ?: return Outcome.Invalid("unknown action ${intent.action}")
        if (def.risk == RiskLevel.RESTRICTED) {
            audit.record(AuditEntry(now(), intent.action.name, def.risk, "refused", intent.rawText.take(80), false))
            return Outcome.Refused("restricted action")
        }
        val missing = def.requiredSlots.filter { s ->
            intent.slots[s].isNullOrBlank() && !(s == Slots.CONTACT && !intent.slots[Slots.CONTACT_REF].isNullOrBlank())
        }
        if (missing.isNotEmpty()) return Outcome.NeedsSlots(missing)
        def.validator(intent)?.let { return Outcome.Invalid(it) }
        val denied = def.permissions.filterNot { permissions.isGranted(it) }.toSet()
        if (denied.isNotEmpty()) {
            audit.record(AuditEntry(now(), intent.action.name, def.risk, "permission_denied:" + denied.joinToString(","), intent.rawText.take(80), confirmed))
            return Outcome.NeedsPermission(denied)
        }
        val prep = def.handler.prepare(intent)
        val ready = when (prep) {
            is Preparation.Failed -> {
                audit.record(AuditEntry(now(), intent.action.name, def.risk, "prepare_failed:" + prep.result.status, prep.result.detail.take(80), confirmed))
                return Outcome.Executed(intent, prep.result)
            }
            is Preparation.Ready -> prep
        }
        if (def.risk == RiskLevel.CONFIRM && !confirmed) return Outcome.NeedsConfirmation(ready.intent, ready.subject)
        val result = try { def.handler.execute(ready.intent) } catch (e: Exception) {
            ActionResult(ResultStatus.FAILED, e.message ?: e::class.java.simpleName)
        }
        audit.record(AuditEntry(now(), intent.action.name, def.risk, result.status.name, (ready.subject ?: intent.rawText).take(80), confirmed))
        return Outcome.Executed(ready.intent, result)
    }
}

/** Level-3 topics that are never executed automatically, whatever the LLM or the user's phrasing. */
object RestrictedTopics {
    private val words = listOf(
        "virement", "transfer money", "send money", "wire transfer", "acheter", "buy ", "purchase", "payer", "pay ", "paiement", "payment", "bank", "banque", "mot de passe", "password",
        "حول فلوس", "حوّل فلوس", "تحويل مالي", "حول المال", "ادفع", "خلص", "اشتري", "شري", "كلمة السر", "كلمه السر", "حساب بنكي", "7awel flous", "7awwel flous", "7awel l flous", "ach3ri", "chri", "khalles", "khales", "flous",
        "supprime mon compte", "delete my account", "factory reset", "reinitialiser le telephone", "حذف الحساب", "امسح الحساب",
    )
    fun matches(text: String): Boolean {
        val t = " " + text.lowercase() + " "
        return words.any { t.contains(it) }
    }
}
