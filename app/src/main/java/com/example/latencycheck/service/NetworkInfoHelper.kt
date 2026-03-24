package com.example.latencycheck.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkInfoHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: com.example.latencycheck.data.AppPreferences
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
    
    private var currentNrType = "UNKNOWN"
    private var isDebugEnabled = false
    private var latestPhyChConfigs: String = "N/A"

    init {
        CoroutineScope(Dispatchers.IO).launch {
            appPreferences.debugEnabled.collect { isDebugEnabled = it }
        }
        registerNrDetector()
    }

    @SuppressLint("MissingPermission")
    private fun registerNrDetector() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val privilegedCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                object : android.telephony.TelephonyCallback(),
                    android.telephony.TelephonyCallback.DisplayInfoListener,
                    android.telephony.TelephonyCallback.ServiceStateListener,
                    android.telephony.TelephonyCallback.PhysicalChannelConfigListener {

                    override fun onDisplayInfoChanged(displayInfo: android.telephony.TelephonyDisplayInfo) {
                        updateCurrentNetworkStatus(null)
                    }

                    override fun onServiceStateChanged(serviceState: android.telephony.ServiceState) {
                        updateCurrentNetworkStatus(serviceState)
                    }

                    override fun onPhysicalChannelConfigChanged(configs: MutableList<android.telephony.PhysicalChannelConfig>) {
                        try {
                            val caInfo = configs.joinToString(", ") { config ->
                                val status = if (config.connectionStatus == android.telephony.PhysicalChannelConfig.CONNECTION_PRIMARY_SERVING) "PCell" else "SCell"
                                "Band:${config.band}(${config.cellBandwidthDownlinkKhz / 1000.0}MHz)-$status"
                            }
                            latestPhyChConfigs = "Channels=${configs.size} [$caInfo]"
                        } catch (e: Exception) {
                            latestPhyChConfigs = "Error parsing config"
                        }
                    }
                }
            } else null

            val standardCallback = object : android.telephony.TelephonyCallback(),
                android.telephony.TelephonyCallback.DisplayInfoListener,
                android.telephony.TelephonyCallback.ServiceStateListener {

                override fun onDisplayInfoChanged(displayInfo: android.telephony.TelephonyDisplayInfo) {
                    updateCurrentNetworkStatus(null)
                }

                override fun onServiceStateChanged(serviceState: android.telephony.ServiceState) {
                    updateCurrentNetworkStatus(serviceState)
                }
            }

            try {
                if (privilegedCallback != null) {
                    telephonyManager.registerTelephonyCallback(context.mainExecutor, privilegedCallback as android.telephony.TelephonyCallback)
                } else {
                    telephonyManager.registerTelephonyCallback(context.mainExecutor, standardCallback)
                }
            } catch (e: SecurityException) {
                Log.w("NetworkInfoHelper", "Privileged listener rejected. Falling back to standard listener.")
                latestPhyChConfigs = "Requires Carrier Privilege"
                try {
                    telephonyManager.registerTelephonyCallback(context.mainExecutor, standardCallback)
                } catch (e2: Exception) {
                    Log.e("NetworkInfoHelper", "Failed to register standard callback", e2)
                }
            } catch (e: Exception) {
                Log.e("NetworkInfoHelper", "Error registering callback", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateCurrentNetworkStatus(serviceState: android.telephony.ServiceState?) {
        val actualNetType = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) telephonyManager.dataNetworkType else telephonyManager.networkType
        } catch (e: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
    
        val oldType = currentNrType
        val ssString = serviceState?.toString() ?: ""
        
        val isEnDc = "isEnDcAvailable\\s*=\\s*true".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(ssString) ||
                     "mIsEnDcAvailable\\s*=\\s*true".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(ssString) ||
                     "EnDc\\s*=\\s*true".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(ssString)

        var hasCellInfoNr = false
        try {
            val cellInfoList = telephonyManager.allCellInfo
            if (cellInfoList != null) {
                for (ci in cellInfoList) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ci is CellInfoNr) {
                        hasCellInfoNr = true
                        break
                    }
                }
            }
        } catch (e: Exception) {}
    
        currentNrType = when {
            actualNetType == TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
            actualNetType == TelephonyManager.NETWORK_TYPE_LTE -> {
                if (isEnDc && hasCellInfoNr) "5G NSA" else "LTE"
            }
            else -> "Other"
        }
    
        if (isDebugEnabled && oldType != currentNrType) {
            Log.d("NetworkInfoHelper", "Status Updated: $oldType -> $currentNrType")
        }
    }

    data class CellData(
        val subscriptionId: Int,
        val isRegistered: Boolean,
        val networkType: String,
        val operatorAlphaShort: String?,
        val cellId: String?,
        val pci: Int?,
        val bandNumber: String?,     // e.g., "B1", "n78"
        val arfcn: Int?,             // EARFCN or NRARFCN
        val signalStrength: Int?,    // RSRP
        val rssi: Int?,
        val rsrq: Int?,
        val sinr: Int?,
        val timingAdvance: Int?,
        val bandwidth: String?
    )

    @Deprecated("Use getAllCellDataList() instead")
    data class NetworkState(
        val type: String,
        val operatorAlphaShort: String?,
        val cellId: String?,
        val pci: Int?,
        val band: String,
        val signalStrength: Int?,
        val bandwidth: String?,
        val neighborCells: String?,
        val timingAdvance: Int?,
        val rssi: Int?,
        val rsrp: Int?,
        val rsrq: Int?,
        val sinr: Int?
    )

    data class DebugInfo(
        // TelephonyDisplayInfo
        val telephonyDisplayInfo: String?,
        val overrideNetworkType: String?,
        // TelephonyManager basic info
        val networkType: String,
        val dataNetworkType: String,
        val voiceNetworkType: String,
        // Operator info
        val operatorName: String?,
        val operatorAlphaShort: String?,
        val operatorAlphaLong: String?,
        val operatorNumeric: String?,
        // Device/Subscription info
        val deviceId: String?,
        val subscriberId: String?,
        val simOperatorName: String?,
        // ServiceState raw
        val serviceStateRaw: String?,
        // SignalStrength raw
        val signalStrengthRaw: String?,
        // CellInfo raw (detailed)
        val cellInfoList: List<String>,
        // Physical Channel Config
        val physicalChannelConfig: String?,
        // Data connection state
        val dataState: String,
        val dataActivity: String,
        // Call state
        val callState: String,
        // All properties map for extensibility
        val allTelephonyProperties: Map<String, String>
    )

    @SuppressLint("MissingPermission")
    fun getCurrentNetworkInfo(): NetworkState {
        var networkType = currentNrType
        var operatorAlphaShort: String? = null
        var cellId: String? = null
        var pci: Int? = null
        var bandInfo = "N/A"
        var signalStrength: Int? = null
        var bandwidthStr: String? = null
        var neighbors = mutableListOf<String>()
        var timingAdvance: Int? = null
        
        var lteRssi: Int? = null
        var lteRsrp: Int? = null
        var lteRsrq: Int? = null
        var lteRssnr: Int? = null
        
        var nrSsRsrp: Int? = null
        var nrSsRsrq: Int? = null
        var nrSsSinr: Int? = null

        try {
            val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.telephony.SubscriptionManager.getActiveDataSubscriptionId()
            } else {
                android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
            }

            val tm = if (subId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                telephonyManager.createForSubscriptionId(subId)
            } else {
                telephonyManager
            }

            // Get operator name
            operatorAlphaShort = tm.serviceState?.operatorAlphaShort

            val realTimeNetType = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tm.dataNetworkType else tm.networkType
            } catch (e: Exception) { TelephonyManager.NETWORK_TYPE_UNKNOWN }

            val ssString = try {
                tm.serviceState?.toString() ?: ""
            } catch (e: Exception) { "" }

            val bwMatch = "mCellBandwidths=\\[([0-9,\\s]+)\\]".toRegex().find(ssString)
            if (bwMatch != null) {
                val bwList = bwMatch.groupValues[1].split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it > 0 }
                    .map { it / 1000 }
                if (bwList.isNotEmpty()) {
                    bandwidthStr = bwList.joinToString("+")
                }
            }

            val isEnDc = "isEnDcAvailable\\s*=\\s*true".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(ssString) ||
                         "mIsEnDcAvailable\\s*=\\s*true".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(ssString) ||
                         "EnDc\\s*=\\s*true".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(ssString)
            var hasCellInfoNr = false

            var lteBandStr = ""
            var nrBandStr = ""

            val activeSubInfo = subscriptionManager.activeSubscriptionInfoList?.find { it.subscriptionId == subId }
            val targetMcc = activeSubInfo?.mccString
            val targetMnc = activeSubInfo?.mncString

            val cellInfoList = tm.allCellInfo
            if (cellInfoList != null) {
                val filteredCellInfo = cellInfoList.filter { cellInfo ->
                    if (!cellInfo.isRegistered) {
                        true
                    } else {
                        val cellSubId = try {
                            val method = cellInfo.javaClass.getMethod("getSubId")
                            method.invoke(cellInfo) as Int
                        } catch (e: Exception) {
                            try {
                                val method = cellInfo.javaClass.getMethod("getSubscriptionId")
                                method.invoke(cellInfo) as Int
                            } catch (e2: Exception) {
                                android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
                            }
                        }

                        if (cellSubId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID && cellSubId != Int.MAX_VALUE) {
                            cellSubId == subId
                        } else if (targetMcc != null && targetMnc != null) {
                            when (cellInfo) {
                                is CellInfoLte -> {
                                    val id = cellInfo.cellIdentity
                                    id.mccString == targetMcc && id.mncString == targetMnc
                                }
                                is CellInfoNr -> {
                                    val nrId = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                                    nrId.mccString == targetMcc && nrId.mncString == targetMnc
                                }
                                else -> true
                            }
                        } else {
                            true
                        }
                    }
                }

                for (cellInfo in filteredCellInfo) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                        hasCellInfoNr = true
                    }

                    if (cellInfo.isRegistered) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                            val nr = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                            if (nrBandStr.isEmpty()) {
                                val derivedBand = if (nr.nrarfcn != Int.MAX_VALUE) getNrBandFromArfcn(nr.nrarfcn) else ""
                                nrBandStr = if (derivedBand.isNotEmpty() && !derivedBand.startsWith("NRARFCN")) {
                                    derivedBand
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && nr.bands.isNotEmpty()) {
                                    "n" + nr.bands.first()
                                } else {
                                    derivedBand
                                }
                            }
                            if (cellInfo.cellSignalStrength is android.telephony.CellSignalStrengthNr) {
                                val ssNr = cellInfo.cellSignalStrength as android.telephony.CellSignalStrengthNr
                                nrSsRsrp = ssNr.ssRsrp
                                nrSsRsrq = ssNr.ssRsrq
                                nrSsSinr = ssNr.ssSinr
                            }
                            // Extract NR Cell ID and PCI
                            if (nr.nci != Long.MAX_VALUE) cellId = nr.nci.toString()
                            if (nr.pci != Int.MAX_VALUE) pci = nr.pci

                        } else if (cellInfo is CellInfoLte) {
                            val lte = cellInfo.cellIdentity
                            if (lteBandStr.isEmpty()) {
                                lteBandStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lte.bands.isNotEmpty()) {
                                    "B" + lte.bands.first()
                                } else {
                                    "EARFCN: ${lte.earfcn}"
                                }
                            }
                            val ssLte = cellInfo.cellSignalStrength
                            lteRsrp = ssLte.rsrp
                            lteRsrq = ssLte.rsrq
                            lteRssnr = ssLte.rssnr
                            lteRssi = ssLte.rssi
                            
                            if (timingAdvance == null) timingAdvance = ssLte.timingAdvance

                            // Extract LTE Cell ID and PCI
                            if (lte.ci != Int.MAX_VALUE) cellId = lte.ci.toString()
                            if (lte.pci != Int.MAX_VALUE) pci = lte.pci
                        }
                    } else {
                        if (cellInfo is CellInfoLte) {
                            val id = cellInfo.cellIdentity
                            val bandStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && id.bands.isNotEmpty()) {
                                "B" + id.bands.joinToString(",")
                            } else ""
                            neighbors.add("LTE:${id.earfcn}/${id.pci}(${cellInfo.cellSignalStrength.rsrp})$bandStr")
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                            val nr = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                            
                            if (nrBandStr.isEmpty()) {
                                val derivedBand = if (nr.nrarfcn != Int.MAX_VALUE) getNrBandFromArfcn(nr.nrarfcn) else ""
                                nrBandStr = if (derivedBand.isNotEmpty() && !derivedBand.startsWith("NRARFCN")) {
                                    derivedBand
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && nr.bands.isNotEmpty()) {
                                    "n" + nr.bands.first()
                                } else {
                                    derivedBand
                                }
                            }
                            
                            val rsrp = if (cellInfo.cellSignalStrength is android.telephony.CellSignalStrengthNr) {
                                (cellInfo.cellSignalStrength as android.telephony.CellSignalStrengthNr).ssRsrp
                            } else "?"
                            neighbors.add("${getNrBandFromArfcn(nr.nrarfcn)}/${nr.pci}($rsrp)")
                        }
                    }
                }
            }

            if (realTimeNetType == TelephonyManager.NETWORK_TYPE_NR) {
                networkType = "5G SA"
            } else if (realTimeNetType == TelephonyManager.NETWORK_TYPE_LTE) {
                if (isEnDc && hasCellInfoNr) {
                    networkType = "5G NSA"
                } else {
                    networkType = "LTE"
                }
            }

            if (networkType == "5G NSA") {
                bandInfo = listOf(lteBandStr, nrBandStr).filter { it.isNotEmpty() }.joinToString("+")
                if (bandInfo.isEmpty()) bandInfo = "N/A"
                signalStrength = lteRsrp ?: nrSsRsrp
            } else if (networkType == "5G SA") {
                bandInfo = if (nrBandStr.isNotEmpty()) nrBandStr else "N/A"
                signalStrength = nrSsRsrp
            } else {
                bandInfo = if (lteBandStr.isNotEmpty()) lteBandStr else "N/A"
                signalStrength = lteRsrp
            }

        } catch (e: Exception) { Log.e("NetworkInfoHelper", "Error in getCurrentNetworkInfo", e) }

        val finalRssi = lteRssi
        val finalRsrp = if (networkType == "5G SA") nrSsRsrp else (lteRsrp ?: nrSsRsrp)
        val finalRsrq = if (networkType == "5G SA") nrSsRsrq else (lteRsrq ?: nrSsRsrq)
        val finalSinr = if (networkType == "5G SA") nrSsSinr else (lteRssnr ?: nrSsSinr)

        val neighborStr = if (neighbors.isNotEmpty()) neighbors.joinToString(";") else null
        val state = NetworkState(
            networkType, operatorAlphaShort, cellId, pci, bandInfo, signalStrength, bandwidthStr, neighborStr, timingAdvance,
            finalRssi, finalRsrp, finalRsrq, finalSinr
        )
        
        if (isDebugEnabled) {
            Log.d("NetworkInfoHelper", "getCurrentNetworkInfo: result=$state")
            dumpRawTelephonyInfo("getCurrentNetworkInfo")
        }
        return state
    }

    // New method: Get cell data from all subscriptions and all cells (registered + unregistered)
    @SuppressLint("MissingPermission")
    fun getAllCellDataList(): List<CellData> {
        val allCellData = mutableListOf<CellData>()

        try {
            // Get all active subscription IDs
            val subIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                subscriptionManager.activeSubscriptionInfoList?.map { it.subscriptionId } ?: listOf(
                    android.telephony.SubscriptionManager.getActiveDataSubscriptionId())
            } else {
                listOf(android.telephony.SubscriptionManager.getDefaultDataSubscriptionId())
            }

            for (subId in subIds) {
                val tm = if (subId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    telephonyManager.createForSubscriptionId(subId)
                } else {
                    telephonyManager
                }

                val operatorAlphaShort = tm.serviceState?.operatorAlphaShort

                // Get network type for this subscription
                val netType = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tm.dataNetworkType else tm.networkType
                } catch (e: Exception) { TelephonyManager.NETWORK_TYPE_UNKNOWN }

                val networkType = when (netType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    else -> "Other"
                }

                // Process all cell info for this subscription
                val cellInfoList = tm.allCellInfo
                cellInfoList?.forEach { cellInfo ->
                    try {
                        val isRegistered = cellInfo.isRegistered

                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr -> {
                                val nr = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                                val ssNr = cellInfo.cellSignalStrength as? android.telephony.CellSignalStrengthNr

                                // Determine band number and ARFCN
                                val arfcn = if (nr.nrarfcn != Int.MAX_VALUE) nr.nrarfcn else null
                                val bandNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && nr.bands.isNotEmpty()) {
                                    "n${nr.bands.first()}"
                                } else {
                                    arfcn?.let { getNrBandFromArfcn(it) } ?: "N/A"
                                }

                                allCellData.add(CellData(
                                    subscriptionId = subId,
                                    isRegistered = isRegistered,
                                    networkType = if (isRegistered) networkType else "NR (Neighbor)",
                                    operatorAlphaShort = operatorAlphaShort,
                                    cellId = if (nr.nci != Long.MAX_VALUE) nr.nci.toString() else null,
                                    pci = if (nr.pci != Int.MAX_VALUE) nr.pci else null,
                                    bandNumber = bandNumber,
                                    arfcn = arfcn,
                                    signalStrength = ssNr?.ssRsrp,
                                    rssi = null, // NR doesn't have RSSI in the same way
                                    rsrq = ssNr?.ssRsrq,
                                    sinr = ssNr?.ssSinr,
                                    timingAdvance = null, // NR may not have TA
                                    bandwidth = null
                                ))
                            }
                            cellInfo is CellInfoLte -> {
                                val lte = cellInfo.cellIdentity
                                val ssLte = cellInfo.cellSignalStrength

                                val earfcn = if (lte.earfcn != Int.MAX_VALUE) lte.earfcn else null
                                val bandNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lte.bands.isNotEmpty()) {
                                    "B${lte.bands.first()}"
                                } else {
                                    earfcn?.let { "EARFCN:$it" } ?: "N/A"
                                }

                                allCellData.add(CellData(
                                    subscriptionId = subId,
                                    isRegistered = isRegistered,
                                    networkType = if (isRegistered) networkType else "LTE (Neighbor)",
                                    operatorAlphaShort = operatorAlphaShort,
                                    cellId = if (lte.ci != Int.MAX_VALUE) lte.ci.toString() else null,
                                    pci = if (lte.pci != Int.MAX_VALUE) lte.pci else null,
                                    bandNumber = if (bandNumber.startsWith("B")) bandNumber else null,
                                    arfcn = earfcn,
                                    signalStrength = ssLte.rsrp,
                                    rssi = ssLte.rssi,
                                    rsrq = ssLte.rsrq,
                                    sinr = ssLte.rssnr,
                                    timingAdvance = ssLte.timingAdvance,
                                    bandwidth = null
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("NetworkInfoHelper", "Error processing cell info", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkInfoHelper", "Error getting all cell data", e)
        }

        return allCellData
    }

    @SuppressLint("MissingPermission")
    fun getDebugInfo(): DebugInfo {
        val telephonyDisplayInfo: String? = null // Updated via callback, stored separately if needed
        val allProps = mutableMapOf<String, String>()

        // Basic TelephonyManager info
        val networkType = try {
            telephonyManager.networkType.let {
                when (it) {
                    TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                    TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
                    TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
                    TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                    else -> "UNKNOWN ($it)"
                }
            }
        } catch (e: Exception) { "Error: ${e.message}" }

        val dataNetworkType = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager.dataNetworkType.let {
                    when (it) {
                        TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
                        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                        else -> "Type: $it"
                    }
                }
            } else "N/A (API < 30)"
        } catch (e: Exception) { "Error: ${e.message}" }

        val voiceNetworkType = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager.voiceNetworkType.toString()
            } else "N/A (API < 30)"
        } catch (e: Exception) { "Error: ${e.message}" }

        // Operator info
        val operatorName = try { telephonyManager.networkOperatorName } catch (e: Exception) { null }
        val operatorAlphaShort = try { telephonyManager.serviceState?.operatorAlphaShort } catch (e: Exception) { null }
        val operatorAlphaLong = try { telephonyManager.serviceState?.operatorAlphaLong } catch (e: Exception) { null }
        val operatorNumeric = try { telephonyManager.serviceState?.operatorNumeric } catch (e: Exception) { null }

        // Device/Subscription
        val deviceId = try { telephonyManager.deviceId } catch (e: Exception) { null }
        val subscriberId = try { telephonyManager.subscriberId } catch (e: Exception) { null }
        val simOperatorName = try { telephonyManager.simOperatorName } catch (e: Exception) { null }

        // ServiceState
        val serviceStateRaw = try {
            telephonyManager.serviceState?.toString()
        } catch (e: Exception) { "Error: ${e.message}" }

        // SignalStrength
        val signalStrengthRaw = try {
            telephonyManager.signalStrength?.toString()
        } catch (e: Exception) { "Error: ${e.message}" }

        // CellInfo list
        val cellInfoList = try {
            telephonyManager.allCellInfo?.mapNotNull { cellInfo ->
                try {
                    when (cellInfo) {
                        is CellInfoLte -> {
                            val id = cellInfo.cellIdentity
                            val ss = cellInfo.cellSignalStrength
                            "LTE [EARFCN:${id.earfcn}, PCI:${id.pci}, CellID:${id.ci}, TAC:${id.tac}, RSRP:${ss.rsrp}, RSRQ:${ss.rsrq}, RSSI:${ss.rssi}, TA:${ss.timingAdvance}, Registered:${cellInfo.isRegistered}]"
                        }
                        is CellInfoNr -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val id = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                                val ss = cellInfo.cellSignalStrength as? android.telephony.CellSignalStrengthNr
                                val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.contentToString() else "N/A"
                                "NR [NRARFCN:${id.nrarfcn}, PCI:${id.pci}, NCI:${id.nci}, TAC:${id.tac}, Bands:$bands, SS-RSRP:${ss?.ssRsrp}, SS-RSRQ:${ss?.ssRsrq}, SS-SINR:${ss?.ssSinr}, Registered:${cellInfo.isRegistered}]"
                            } else null
                        }
                        else -> "${cellInfo.javaClass.simpleName}: ${cellInfo.toString().take(200)}"
                    }
                } catch (e: Exception) { "Error parsing cell: ${e.message}" }
            } ?: listOf("No cell info available")
        } catch (e: Exception) { listOf("Error: ${e.message}") }

        // Data state and activity
        val dataState = try {
            when (telephonyManager.dataState) {
                TelephonyManager.DATA_CONNECTED -> "CONNECTED"
                TelephonyManager.DATA_CONNECTING -> "CONNECTING"
                TelephonyManager.DATA_DISCONNECTED -> "DISCONNECTED"
                TelephonyManager.DATA_SUSPENDED -> "SUSPENDED"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) { "Error: ${e.message}" }

        val dataActivity = try {
            when (telephonyManager.dataActivity) {
                TelephonyManager.DATA_ACTIVITY_IN -> "IN"
                TelephonyManager.DATA_ACTIVITY_OUT -> "OUT"
                TelephonyManager.DATA_ACTIVITY_INOUT -> "INOUT"
                TelephonyManager.DATA_ACTIVITY_NONE -> "NONE"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) { "Error: ${e.message}" }

        val callState = try {
            when (telephonyManager.callState) {
                TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) { "Error: ${e.message}" }

        // Collect additional properties
        try {
            telephonyManager.javaClass.methods
                .filter { it.name.startsWith("get") && it.parameterTypes.isEmpty() }
                .sortedBy { it.name }
                .forEach { method ->
                    try {
                        val value = method.invoke(telephonyManager)?.toString() ?: "null"
                        if (value.length < 500) { // Skip very long strings
                            allProps[method.name] = value
                        }
                    } catch (e: Exception) { /* skip */ }
                }
        } catch (e: Exception) { /* ignore */ }

        return DebugInfo(
            telephonyDisplayInfo = telephonyDisplayInfo,
            overrideNetworkType = currentNrType,
            networkType = networkType,
            dataNetworkType = dataNetworkType,
            voiceNetworkType = voiceNetworkType,
            operatorName = operatorName,
            operatorAlphaShort = operatorAlphaShort,
            operatorAlphaLong = operatorAlphaLong,
            operatorNumeric = operatorNumeric,
            deviceId = deviceId,
            subscriberId = subscriberId,
            simOperatorName = simOperatorName,
            serviceStateRaw = serviceStateRaw,
            signalStrengthRaw = signalStrengthRaw,
            cellInfoList = cellInfoList,
            physicalChannelConfig = latestPhyChConfigs,
            dataState = dataState,
            dataActivity = dataActivity,
            callState = callState,
            allTelephonyProperties = allProps
        )
    }

    // ★ 新規追加: 3GPP仕様に基づく NRARFCN → バンド変換関数
    private fun getNrBandFromArfcn(nrarfcn: Int): String {
        return when (nrarfcn) {
            in 151600..160600 -> "n28" // 700 MHz
            in 173800..178800 -> "n5"
            in 185000..192000 -> "n8"
            in 356000..376000 -> "n3"  // 1.8 GHz
            in 386000..398000 -> "n2"
            in 422000..434000 -> "n1"  // 2.1 GHz
            in 460000..480000 -> "n40"
            in 499200..537999 -> "n41" // 2.5 GHz
            in 620000..680000 -> "n77/n78" // 3.4 - 3.8 GHz (日本の主要Sub6)
            in 680001..693333 -> "n77" // 3.8 - 4.2 GHz
            in 693334..719999 -> "n79" // n79と重なる帯域
            in 720000..733333 -> "n79" // 4.5 GHz
            in 2054166..2104165 -> "n257" // 28 GHz (ミリ波)
            else -> "NRARFCN:$nrarfcn" // 未知の帯域の場合はそのまま出力
        }
    }

    @SuppressLint("MissingPermission")
    private fun dumpRawTelephonyInfo(contextTag: String = "dump") {
        if (!isDebugEnabled) return
        
        try {
            val sb = StringBuilder()
            sb.append("DumpTelephonyInfo [$contextTag]: ")
            
            try {
                val ss = telephonyManager.serviceState
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ss != null) {
                    val isCaActive = ss.toString().contains("isUsingCarrierAggregation=true")
                    sb.append("isCA_Active=$isCaActive; ")
                }
            } catch (e: Exception) { }

            sb.append("PhysicalChannels=[$latestPhyChConfigs]; ")
            sb.append("networkType=${telephonyManager.networkType}; ")

            try {
                val dataNetMethod = telephonyManager.javaClass.getMethod("getDataNetworkType")
                val dnt = dataNetMethod.invoke(telephonyManager) as? Int
                sb.append("dataNetworkType=$dnt; ")
            } catch (e: Exception) { /* ignore */ }

            try {
                val ss = telephonyManager.serviceState
                val ssCompact = ss?.toString()?.replace('\n', ' ') ?: "null"
                sb.append("ServiceState=[$ssCompact]; ")
            } catch (e: Exception) { }

            try {
                val cellInfoList = telephonyManager.allCellInfo
                sb.append("cellInfoCount=${cellInfoList?.size ?: 0}; ")
                if (!cellInfoList.isNullOrEmpty()) {
                    for (ci in cellInfoList) {
                        try {
                            sb.append("[registered=${ci.isRegistered}, class=${ci.javaClass.simpleName}")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ci is CellInfoNr) {
                                val nr = ci.cellIdentity as android.telephony.CellIdentityNr
                                sb.append(",nrarfcn=${nr.nrarfcn},pci=${nr.pci}")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sb.append(",bands=${nr.bands.joinToString(",")}")
                            } else if (ci is CellInfoLte) {
                                val lte = ci.cellIdentity
                                sb.append(",earfcn=${lte.earfcn},pci=${lte.pci}")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sb.append(",bands=${lte.bands.joinToString(",")}")
                            }
                            sb.append("];")
                        } catch (e: Exception) { /* per-cell guard */ }
                    }
                }
            } catch (e: Exception) { /* ignore */ }

            Log.d("NetworkInfoHelper", sb.toString())
        } catch (e: Exception) {
            Log.w("NetworkInfoHelper", "Failed to dump telephony info", e)
        }
    }
}