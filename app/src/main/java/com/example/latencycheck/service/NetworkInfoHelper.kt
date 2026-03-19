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

    init {
        CoroutineScope(Dispatchers.IO).launch {
            appPreferences.debugEnabled.collect { isDebugEnabled = it }
        }
        registerNrDetector()
    }

    private fun registerNrDetector() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val callback = object : android.telephony.TelephonyCallback(),
                    android.telephony.TelephonyCallback.DisplayInfoListener,
                    android.telephony.TelephonyCallback.ServiceStateListener {

                    override fun onDisplayInfoChanged(displayInfo: android.telephony.TelephonyDisplayInfo) {
                        // UI表示用のフラグ(NSA表示)は、物理的にLTEに落ちていても残るため参考程度にする
                        updateCurrentNetworkStatus(null)
                    }

                    override fun onServiceStateChanged(serviceState: android.telephony.ServiceState) {
                        updateCurrentNetworkStatus(serviceState)
                    }

                    private fun updateCurrentNetworkStatus(serviceState: android.telephony.ServiceState?) {
                        val actualNetType = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                telephonyManager.dataNetworkType
                            } else {
                                telephonyManager.networkType
                            }
                        } catch (e: SecurityException) {
                            TelephonyManager.NETWORK_TYPE_UNKNOWN
                        }
                    
                        val oldType = currentNrType
                        val ssString = serviceState?.toString() ?: ""
                    
                        currentNrType = when {
                            // 1. 物理ネットワーク型が NR(20) なら SA 確定
                            actualNetType == TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
                    
                            // 2. 物理ネットワークが LTE(13) の場合の厳密な判定
                            actualNetType == TelephonyManager.NETWORK_TYPE_LTE -> {
                                // "nrState=CONNECTED" は「実際にNRの電波を掴んで通信中」を意味する。
                                // "NOT_RESTRICTED" は「LTEに繋がっていて5G利用可能(アンテナには5Gと出る)」状態なので、物理的にはLTEとして扱う
                                if (ssString.contains("nrState=CONNECTED")) {
                                    "5G NSA"
                                } else {
                                    "LTE"
                                }
                            }
                    
                            else -> "Other"
                        }
                    
                        if (isDebugEnabled && oldType != currentNrType) {
                            Log.d("NetworkInfoHelper", "Status Updated: $oldType -> $currentNrType (NetType=$actualNetType)")
                        }
                    }
                }
        
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } catch (e: Exception) {
                Log.e("NetworkInfoHelper", "Error registering callback", e)
            }
        }
    }

    data class NetworkState(
        val type: String,
        val band: String,
        val signalStrength: Int?,
        val bandwidth: Int?,
        val neighborCells: String?,
        val timingAdvance: Int?
    )

    @SuppressLint("MissingPermission")
    fun getCurrentNetworkInfo(): NetworkState {
        var networkType = currentNrType
        var bandInfo = "N/A"
        var signalStrength: Int? = null
        var bandwidth: Int? = null
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

            // --- リアルタイム判定の強化 ---
            val realTimeNetType = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tm.dataNetworkType else tm.networkType
            } catch (e: Exception) { TelephonyManager.NETWORK_TYPE_UNKNOWN }

            val ssString = try {
                tm.serviceState?.toString() ?: ""
            } catch (e: Exception) { "" }

            // 呼び出し時点の物理状態を最優先
            if (realTimeNetType == TelephonyManager.NETWORK_TYPE_NR) {
                networkType = "5G SA"
            } else if (realTimeNetType == TelephonyManager.NETWORK_TYPE_LTE) {
                networkType = if (ssString.contains("nrState=CONNECTED")) "5G NSA" else "LTE"
            }

            val activeSubInfo = subscriptionManager.activeSubscriptionInfoList?.find { it.subscriptionId == subId }
            val targetMcc = activeSubInfo?.mccString
            val targetMnc = activeSubInfo?.mncString

            val cellInfoList = tm.allCellInfo
            var hasRegisteredNr = false
            var hasRegisteredLte = false
            
            if (cellInfoList != null) {
                val filteredCellInfo = cellInfoList.filter { cellInfo ->
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

                    if (cellSubId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        cellSubId == subId
                    } else if (targetMcc != null && targetMnc != null) {
                        when (cellInfo) {
                            is CellInfoLte -> cellInfo.cellIdentity.mccString == targetMcc && cellInfo.cellIdentity.mncString == targetMnc
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

                for (cellInfo in filteredCellInfo) {
                    if (cellInfo.isRegistered) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                            hasRegisteredNr = true
                            networkType = if (realTimeNetType == TelephonyManager.NETWORK_TYPE_NR) "5G SA" else "5G NSA"
                            val nr = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                            bandInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && nr.bands.isNotEmpty()) {
                                "n" + nr.bands.joinToString(",")
                            } else {
                                "NRARFCN: ${nr.nrarfcn}"
                            }
                            if (cellInfo.cellSignalStrength is android.telephony.CellSignalStrengthNr) {
                                signalStrength = (cellInfo.cellSignalStrength as android.telephony.CellSignalStrengthNr).ssRsrp
                            }
                        } else if (cellInfo is CellInfoLte) {
                            hasRegisteredLte = true
                            // NSA環境下でLTEがプライマリセルの場合、CellInfoNrよりもこちらが優先されて上書きされるのを防ぐためbandInfoの上書き条件を調整
                            if (!hasRegisteredNr || bandInfo == "N/A") {
                                val lte = cellInfo.cellIdentity
                                bandInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lte.bands.isNotEmpty()) {
                                    "B" + lte.bands.joinToString(",")
                                } else {
                                    "EARFCN: ${lte.earfcn}"
                                }
                                bandwidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lte.bandwidth else null
                                signalStrength = cellInfo.cellSignalStrength.rsrp
                                timingAdvance = cellInfo.cellSignalStrength.timingAdvance
                            }
                        }
                    } else {
                        if (cellInfo is CellInfoLte) {
                            val id = cellInfo.cellIdentity
                            val bandStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && id.bands.isNotEmpty()) {
                                "B" + id.bands.joinToString(",")
                            } else ""
                            val label = "LTE:${id.earfcn}/${id.pci}(${cellInfo.cellSignalStrength.rsrp})$bandStr"
                            neighbors.add(label)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                            val nr = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                            val bandStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && nr.bands.isNotEmpty()) {
                                "n" + nr.bands.joinToString(",")
                            } else ""
                            val rsrp = if (cellInfo.cellSignalStrength is android.telephony.CellSignalStrengthNr) {
                                (cellInfo.cellSignalStrength as android.telephony.CellSignalStrengthNr).ssRsrp
                            } else "?"
                            neighbors.add("NR:${nr.nrarfcn}/${nr.pci}($rsrp)$bandStr")
                        }
                    }
                }
            }

            // --- 最終補正: セル情報に基づく強制補正 ---
            // LTEセルしか掴んでいない（CellInfoにNRがない）かつ、リアルタイムのServiceStateもCONNECTEDでない場合は、完全にLTEと見なす
            if (hasRegisteredLte && !hasRegisteredNr && !ssString.contains("nrState=CONNECTED")) {
                networkType = "LTE"
            }

        } catch (e: Exception) { }

        val neighborStr = if (neighbors.isNotEmpty()) neighbors.joinToString(";") else null
        val state = NetworkState(networkType, bandInfo, signalStrength, bandwidth, neighborStr, timingAdvance)
        
        if (isDebugEnabled) {
            Log.d("NetworkInfoHelper", "getCurrentNetworkInfo: result=$state")
            dumpRawTelephonyInfo("getCurrentNetworkInfo")
        }
        return state
    }

    @SuppressLint("MissingPermission")
    private fun dumpRawTelephonyInfo(contextTag: String = "dump") {
        if (!isDebugEnabled) return
        
        try {
            val sb = StringBuilder()
            sb.append("DumpTelephonyInfo [$contextTag]: ")
            sb.append("networkType=${telephonyManager.networkType}; ")
            try {
                val dataNetMethod = telephonyManager.javaClass.getMethod("getDataNetworkType")
                val dnt = dataNetMethod.invoke(telephonyManager) as? Int
                sb.append("dataNetworkType=$dnt; ")
            } catch (e: Exception) { /* ignore */ }

            // ★ デバッグログ強化：ServiceState(モデムの生ステータス) をダンプに追加
            try {
                val ss = telephonyManager.serviceState
                val ssCompact = ss?.toString()?.replace('\n', ' ')?.take(200) ?: "null"
                sb.append("ServiceState=[$ssCompact...]; ")
            } catch (e: Exception) { }

            val subId = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.telephony.SubscriptionManager.getActiveDataSubscriptionId() else android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
            } catch (e: Exception) { -1 }
            sb.append("subId=$subId; ")

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
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sb.append(",bands=${nr.bands?.joinToString(",")}")
                            } else if (ci is CellInfoLte) {
                                val lte = ci.cellIdentity
                                sb.append(",earfcn=${lte.earfcn},pci=${lte.pci}")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sb.append(",bands=${lte.bands?.joinToString(",")}")
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