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

    data class NetworkState(
        val type: String,
        val band: String,
        val signalStrength: Int?,
        val bandwidth: String?, 
        val neighborCells: String?,
        val timingAdvance: Int?
    )

    @SuppressLint("MissingPermission")
    fun getCurrentNetworkInfo(): NetworkState {
        var networkType = currentNrType
        var bandInfo = "N/A"
        var signalStrength: Int? = null
        var bandwidthStr: String? = null
        var neighbors = mutableListOf<String>()
        var timingAdvance: Int? = null

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
            var lteRsrp: Int? = null
            var nrRsrp: Int? = null

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
                                nrBandStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && nr.bands.isNotEmpty()) {
                                    "n" + nr.bands.first()
                                } else {
                                    getNrBandFromArfcn(nr.nrarfcn) // ★ 修正箇所
                                }
                            }
                            if (cellInfo.cellSignalStrength is android.telephony.CellSignalStrengthNr) {
                                nrRsrp = (cellInfo.cellSignalStrength as android.telephony.CellSignalStrengthNr).ssRsrp
                            }
                        } else if (cellInfo is CellInfoLte) {
                            val lte = cellInfo.cellIdentity
                            if (lteBandStr.isEmpty()) {
                                lteBandStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lte.bands.isNotEmpty()) {
                                    "B" + lte.bands.first()
                                } else {
                                    "EARFCN: ${lte.earfcn}"
                                }
                            }
                            lteRsrp = cellInfo.cellSignalStrength.rsrp
                            if (timingAdvance == null) timingAdvance = cellInfo.cellSignalStrength.timingAdvance
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
                                nrBandStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && nr.bands.isNotEmpty()) {
                                    "n" + nr.bands.first()
                                } else {
                                    getNrBandFromArfcn(nr.nrarfcn) // ★ 修正箇所
                                }
                            }
                            
                            val rsrp = if (cellInfo.cellSignalStrength is android.telephony.CellSignalStrengthNr) {
                                (cellInfo.cellSignalStrength as android.telephony.CellSignalStrengthNr).ssRsrp
                            } else "?"
                            neighbors.add("${getNrBandFromArfcn(nr.nrarfcn)}/${nr.pci}($rsrp)") // ★ 修正箇所
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
                signalStrength = lteRsrp ?: nrRsrp
            } else if (networkType == "5G SA") {
                bandInfo = if (nrBandStr.isNotEmpty()) nrBandStr else "N/A"
                signalStrength = nrRsrp
            } else {
                bandInfo = if (lteBandStr.isNotEmpty()) lteBandStr else "N/A"
                signalStrength = lteRsrp
            }

        } catch (e: Exception) { }

        val neighborStr = if (neighbors.isNotEmpty()) neighbors.joinToString(";") else null
        val state = NetworkState(networkType, bandInfo, signalStrength, bandwidthStr, neighborStr, timingAdvance)
        
        if (isDebugEnabled) {
            Log.d("NetworkInfoHelper", "getCurrentNetworkInfo: result=$state")
            dumpRawTelephonyInfo("getCurrentNetworkInfo")
        }
        return state
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
            in 693334..719999 -> "n77/n79" // n79と重なる帯域
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