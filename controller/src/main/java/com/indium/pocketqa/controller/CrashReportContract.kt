package com.indium.pocketqa.controller

/** Shared v1 boundary contract for Buggy App crash reports. */
object CrashReportContract {
    const val targetPackage = "com.pocketqa.pocketqa"
    const val authority = "com.pocketqa.pocketqa.crashes"
    const val latestUri = "content://$authority/latest"
    const val readPermission = "com.pocketqa.pocketqa.permission.READ_CRASH_REPORT"
    const val reportColumn = "report_json"

    fun validate(
        schemaVersion: Int,
        id: String,
        appPackage: String,
        sourceKey: String?,
        line: Int?,
    ): ContractValidation {
        if (schemaVersion != 1) return ContractValidation.invalid("schemaVersion")
        if (id.isBlank()) return ContractValidation.invalid("id")
        if (appPackage != targetPackage) return ContractValidation.invalid("appPackage")
        if (sourceKey != null && (!sourceKey.startsWith("lib/") || sourceKey.contains(".."))) {
            return ContractValidation.invalid("sourceKey")
        }
        if (line != null && line < 1) return ContractValidation.invalid("line")
        return ContractValidation.valid()
    }
}

data class ContractValidation(val isValid: Boolean, val reason: String? = null) {
    companion object {
        fun valid() = ContractValidation(isValid = true)
        fun invalid(reason: String) = ContractValidation(isValid = false, reason = reason)
    }
}
