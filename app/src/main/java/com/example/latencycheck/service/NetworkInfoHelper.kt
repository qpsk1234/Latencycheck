package com.example.latencycheck.service

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
    @ApplicationContext private val context: Context
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
    
    private var currentNrType = "UNKNOWN"

    init {
        registerNrDetector()
    }

    private fun registerNrDetector() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val callback = object : android.telephony.TelephonyCallback(), 
                    android.telephony.TelephonyCallback.DisplayInfoListener,
                    android.telephony.TelephonyCallback.ServiceStateListener {
                    
                    override fun onDisplayInfoChanged(displayInfo: android.telephony.TelephonyDisplayInfo) {
                        val overrideType = displayInfo.overrideNetworkType
                        val oldType = currentNrType
                        currentNrType = when (overrideType) {
                            android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G NSA"
                            android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G NSA+"
                            else -> if (telephonyManager.networkType == TelephonyManager.NETWORK_TYPE_NR) "5G SA" else "LTE"
                        }
                        Log.d("NetworkInfoHelper", "DisplayInfoChanged: override=$overrideType, networkType=${telephonyManager.networkType}, result=$currentNrType (was $oldType)")
                    }

                    override fun onServiceStateChanged(serviceState: android.telephony.ServiceState) {
                        val oldType = currentNrType
                        // Re-evaluate on service state change (e.g. SA/LTE handover)
                        if (telephonyManager.networkType == TelephonyManager.NETWORK_TYPE_NR) {
                            currentNrType = "5G SA"
                        } else if (currentNrType == "5G SA") {
                            currentNrType = "LTE" // Fallback if no longer NR
                        }
                        Log.d("NetworkInfoHelper", "ServiceStateChanged: networkType=${telephonyManager.networkType}, result=$currentNrType (was $oldType)")
                    }
                }
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } catch (e: Exception) { 
                Log.e("NetworkInfoHelper", "Error registering callback", e)
            }
        }
    }

    data class NetworkState(
        val type: String, // "LTE", "5G NSA", "5G SA", etc.
        val band: String,
        val signalStrength: Int?,
        val bandwidth: Int?,
        val neighborCells: String?,
        val timingAdvance: Int?
    )

    @SuppressLint("MissingPermission") // User will handle permissions
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

            // If we are on a different subID, we might need a separate listener or just fallback
            if (networkType == "UNKNOWN") {
                networkType = if (tm.networkType == TelephonyManager.NETWORK_TYPE_NR) "5G SA" else "LTE"
            }

            val activeSubInfo = subscriptionManager.activeSubscriptionInfoList?.find { it.subscriptionId == subId }
            val targetMcc = activeSubInfo?.mccString
            val targetMnc = activeSubInfo?.mncString

            val cellInfoList = tm.allCellInfo
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
                    } else {
                        // Neighbor cell info
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
        } catch (e: Exception) { }

        val neighborStr = if (neighbors.isNotEmpty()) neighbors.joinToString(";") else null
        val state = NetworkState(networkType, bandInfo, signalStrength, bandwidth, neighborStr, timingAdvance)
        Log.d("NetworkInfoHelper", "getCurrentNetworkInfo: result=$state")
        return state
    }
}
