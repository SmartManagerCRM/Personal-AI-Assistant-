package com.smartmanager.ai.actions

import com.smartmanager.ai.core.action.*
import com.smartmanager.ai.core.intent.ActionName
import com.smartmanager.ai.core.intent.Slots
import com.smartmanager.ai.data.AppDatabase
import com.smartmanager.ai.data.AuditEntity
import com.smartmanager.ai.perm.PermissionManager
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the ActionRegistry: names, validated slots, permissions, risk level and handlers. This is the only door to the phone. */
@Singleton
class ActionRegistryFactory @Inject constructor(private val a: PhoneActions, private val perms: PermissionManager, private val db: AppDatabase) {
    fun create(): ActionRegistry = ActionRegistry(perms, AuditLog { e ->
        db.dao().addAudit(AuditEntity(timeMs = e.timeMs, action = e.action, risk = e.risk.name, outcome = e.outcome, summary = e.summary, confirmed = e.confirmed))
    }).apply {
        register(ActionDefinition(ActionName.CALL_CONTACT, setOf(Slots.CONTACT), setOf(AppPermission.CONTACTS), RiskLevel.CONFIRM, handler = a.call))
        register(ActionDefinition(ActionName.SEND_MESSAGE, setOf(Slots.CONTACT, Slots.BODY), setOf(AppPermission.CONTACTS), RiskLevel.CONFIRM, handler = a.message))
        register(ActionDefinition(ActionName.SHARE_LAST_PHOTO, emptySet(), setOf(AppPermission.MEDIA), RiskLevel.CONFIRM, handler = a.shareLastPhoto))
        register(ActionDefinition(ActionName.OPEN_APP, setOf(Slots.APP), emptySet(), RiskLevel.AUTOMATIC, handler = a.openApp))
        register(ActionDefinition(ActionName.TAKE_PHOTO, emptySet(), setOf(AppPermission.CAMERA), RiskLevel.AUTOMATIC, handler = a.camera(video = false)))
        register(ActionDefinition(ActionName.RECORD_VIDEO, emptySet(), setOf(AppPermission.CAMERA, AppPermission.MICROPHONE), RiskLevel.AUTOMATIC, handler = a.camera(video = true)))
        val needsTime: (com.smartmanager.ai.core.intent.ParsedIntent) -> String? = { i -> if (listOf(Slots.MINUTES, Slots.DAY_OFFSET, Slots.HOUR).none { !i.slot(it).isNullOrBlank() }) "missing time" else null }
        register(ActionDefinition(ActionName.SET_REMINDER, emptySet(), emptySet(), RiskLevel.AUTOMATIC, validator = needsTime, handler = a.reminder))
        register(ActionDefinition(ActionName.SET_ALARM, emptySet(), emptySet(), RiskLevel.AUTOMATIC, validator = needsTime, handler = a.alarm))
        register(ActionDefinition(ActionName.READ_AGENDA, emptySet(), setOf(AppPermission.CALENDAR), RiskLevel.AUTOMATIC, handler = a.agenda))
        register(ActionDefinition(ActionName.SET_VOLUME, emptySet(), emptySet(), RiskLevel.AUTOMATIC, handler = a.volume))
        register(ActionDefinition(ActionName.OPEN_SETTINGS_PANEL, emptySet(), emptySet(), RiskLevel.AUTOMATIC, handler = a.settingsPanel))
        register(ActionDefinition(ActionName.READ_NOTIFICATIONS, emptySet(), setOf(AppPermission.NOTIFICATION_ACCESS), RiskLevel.AUTOMATIC, handler = a.notifications))
    }
}
