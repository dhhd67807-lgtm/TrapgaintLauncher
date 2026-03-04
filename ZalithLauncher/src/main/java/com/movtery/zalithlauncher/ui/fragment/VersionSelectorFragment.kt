package com.movtery.zalithlauncher.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentVersionBinding
import com.movtery.zalithlauncher.event.value.InstallGameEvent
import com.movtery.zalithlauncher.feature.mod.modloader.OptiFineDownloadTask
import com.movtery.zalithlauncher.feature.version.install.Addon
import com.movtery.zalithlauncher.feature.version.install.InstallTaskItem
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.subassembly.versionlist.VersionSelectedListener
import com.movtery.zalithlauncher.ui.subassembly.versionlist.VersionType
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.setDebouncedClickListener
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.OptiFineUtils
import net.kdt.pojavlaunch.modloaders.ForgeUtils
import org.greenrobot.eventbus.EventBus

class VersionSelectorFragment : FragmentWithAnim(R.layout.fragment_version) {
    companion object {
        const val TAG: String = "FileSelectorFragment"
    }

    private lateinit var binding: FragmentVersionBinding
    private var release: TabLayout.Tab? = null
    private var snapshot: TabLayout.Tab? = null
    private var beta: TabLayout.Tab? = null
    private var alpha: TabLayout.Tab? = null
    private var versionType: VersionType? = null
    private var loaderType: String = "VANILLA"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVersionBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindTab()
        
        // Get loader type and selected version from arguments
        loaderType = arguments?.getString("LOADER_TYPE") ?: "VANILLA"
        val selectedVersion = arguments?.getString("SELECTED_VERSION")

        binding.apply {
            // Set the icon based on loader type
            val loaderIcon = when (loaderType) {
                "VANILLA" -> R.drawable.ic_vanilla
                "FABRIC" -> R.drawable.ic_fabric_loader
                "FORGE" -> R.drawable.ic_forge
                else -> R.drawable.ic_vanilla
            }
            
            // Update version list to use the loader icon
            version.setLoaderIcon(loaderIcon)
            
            // Set version filter if a specific version was selected (e.g., "1.21")
            if (selectedVersion != null) {
                version.setVersionFilter(selectedVersion)
            }
            
            refresh(versionTab.getTabAt(versionTab.selectedTabPosition))

            versionTab.addOnTabSelectedListener(object : OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    refresh(tab)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                }
            })

            searchVersion.doAfterTextChanged { text ->
                val string = text?.toString() ?: ""
                version.setFilterString(string)
            }

            returnButton.setDebouncedClickListener { ZHTools.onBackPressed(requireActivity()) }

            version.setVersionSelectedListener(object : VersionSelectedListener() {
                override fun onVersionSelected(version: String?) {
                    if (version == null) {
                        Tools.backToMainMenu(requireActivity())
                    } else {
                        // Auto-install with the appropriate loader based on loaderType
                        when (loaderType) {
                            "VANILLA" -> startAutoInstallWithOptiFine(version)
                            "FABRIC" -> startAutoInstallWithFabric(version)
                            "FORGE" -> startAutoInstallWithForge(version)
                            else -> startAutoInstallWithOptiFine(version)
                        }
                    }
                }
            })
        }
    }
    
    private fun startAutoInstallWithOptiFine(mcVersion: String) {
        // Get activity reference before navigating away
        val activity = requireActivity()
        
        // Navigate back to home immediately
        Tools.backToMainMenu(activity)
        
        // Fetch OptiFine versions in background
        TaskExecutors.getDefault().submit {
            try {
                val optiFineVersions = OptiFineUtils.downloadOptiFineVersions(false)
                
                // Find OptiFine versions for this MC version
                val mcOptiFineVersions = mutableListOf<OptiFineUtils.OptiFineVersion>()
                optiFineVersions?.optifineVersions?.forEach { versionList: List<OptiFineUtils.OptiFineVersion> ->
                    versionList.forEach { optiFineVersion: OptiFineUtils.OptiFineVersion ->
                        val cleanMcVersion = optiFineVersion.minecraftVersion.removePrefix("Minecraft").trim()
                        if (cleanMcVersion == mcVersion) {
                            mcOptiFineVersions.add(optiFineVersion)
                        }
                    }
                }
                
                if (mcOptiFineVersions.isEmpty()) {
                    // No OptiFine available, install vanilla only
                    EventBus.getDefault().post(
                        InstallGameEvent(
                            mcVersion,
                            mcVersion,
                            emptyMap(),
                            "VANILLA"
                        )
                    )
                } else {
                    // Use the first (latest) OptiFine version
                    val latestOptiFine = mcOptiFineVersions[0]
                    val customVersionName = "$mcVersion OptiFine"
                    
                    // Create task map with OptiFine
                    val taskMap = mapOf(
                        Addon.OPTIFINE to InstallTaskItem(
                            latestOptiFine.versionName,
                            false,
                            OptiFineDownloadTask(latestOptiFine),
                            null
                        )
                    )
                    
                    // Post installation event
                    EventBus.getDefault().post(
                        InstallGameEvent(
                            mcVersion,
                            customVersionName,
                            taskMap,
                            "VANILLA"
                        )
                    )
                }
            } catch (e: Exception) {
                // Install vanilla as fallback
                EventBus.getDefault().post(
                    InstallGameEvent(
                        mcVersion,
                        mcVersion,
                        emptyMap(),
                        "VANILLA"
                    )
                )
            }
        }
    }
    
    private fun startAutoInstallWithFabric(mcVersion: String) {
        val activity = requireActivity()
        Tools.backToMainMenu(activity)
        
        android.util.Log.d("VersionSelector", "Starting Fabric installation for MC version: $mcVersion")
        
        TaskExecutors.getDefault().submit {
            try {
                val fabricUtils = com.movtery.zalithlauncher.feature.mod.modloader.FabricLikeUtils.FABRIC_UTILS
                val gameVersions = fabricUtils.downloadGameVersions(false)
                val loaderVersions = fabricUtils.downloadLoaderVersions(false)
                
                android.util.Log.d("VersionSelector", "Fabric loader versions count: ${loaderVersions?.size ?: 0}")
                
                if (loaderVersions.isNullOrEmpty()) {
                    android.util.Log.w("VersionSelector", "No Fabric loader versions found, installing vanilla")
                    // Install vanilla as fallback
                    EventBus.getDefault().post(InstallGameEvent(mcVersion, mcVersion, emptyMap(), "FABRIC"))
                } else {
                    // Use the first (latest stable) Fabric loader version
                    val latestFabric = loaderVersions.firstOrNull { it.stable } ?: loaderVersions[0]
                    val customVersionName = "$mcVersion Fabric"
                    
                    android.util.Log.d("VersionSelector", "Installing Fabric version: ${latestFabric.version} for MC $mcVersion")
                    
                    val taskMap = mapOf(
                        Addon.FABRIC to InstallTaskItem(
                            latestFabric.version,
                            false,
                            fabricUtils.getDownloadTask(mcVersion, latestFabric.version),
                            null
                        )
                    )
                    
                    EventBus.getDefault().post(InstallGameEvent(mcVersion, customVersionName, taskMap, "FABRIC"))
                }
            } catch (e: Exception) {
                android.util.Log.e("VersionSelector", "Error installing Fabric", e)
                EventBus.getDefault().post(InstallGameEvent(mcVersion, mcVersion, emptyMap(), "FABRIC"))
            }
        }
    }
    
    private fun startAutoInstallWithForge(mcVersion: String) {
        val activity = requireActivity()
        Tools.backToMainMenu(activity)
        
        android.util.Log.d("VersionSelector", "Starting Forge installation for MC version: $mcVersion")
        
        TaskExecutors.getDefault().submit {
            try {
                // For Forge, we need to download the version list and find a compatible version
                val forgeVersions: List<String>? = ForgeUtils.downloadForgeVersions(false)
                
                android.util.Log.d("VersionSelector", "Forge versions count: ${forgeVersions?.size ?: 0}")
                
                if (forgeVersions.isNullOrEmpty()) {
                    android.util.Log.w("VersionSelector", "No Forge versions found, installing vanilla")
                    // No Forge available, install vanilla
                    EventBus.getDefault().post(InstallGameEvent(mcVersion, mcVersion, emptyMap(), "FORGE"))
                } else {
                    // Find the first Forge version that matches this MC version
                    val compatibleForge: String? = forgeVersions.firstOrNull { version: String -> 
                        version.startsWith("$mcVersion-")
                    }
                    
                    android.util.Log.d("VersionSelector", "Compatible Forge version: $compatibleForge")
                    
                    if (compatibleForge != null) {
                        val customVersionName = "$mcVersion Forge"
                        // Extract loader version from full version (e.g., "1.21.8-58.0.3" -> "58.0.3")
                        val loaderVersion = compatibleForge.substringAfter("-")
                        android.util.Log.d("VersionSelector", "Extracted loader version: $loaderVersion")
                        
                        val forgeTask = com.movtery.zalithlauncher.feature.mod.modloader.ForgeDownloadTask(mcVersion, loaderVersion)
                        
                        val taskMap = mapOf(
                            Addon.FORGE to InstallTaskItem(
                                compatibleForge,
                                false,
                                forgeTask,
                                null
                            )
                        )
                        
                        EventBus.getDefault().post(InstallGameEvent(mcVersion, customVersionName, taskMap, "FORGE"))
                    } else {
                        android.util.Log.w("VersionSelector", "No compatible Forge version found for MC $mcVersion")
                        // No compatible Forge version found, install vanilla
                        EventBus.getDefault().post(InstallGameEvent(mcVersion, mcVersion, emptyMap(), "FORGE"))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VersionSelector", "Error installing Forge", e)
                EventBus.getDefault().post(InstallGameEvent(mcVersion, mcVersion, emptyMap(), "FORGE"))
            }
        }
    }

    private fun refresh(tab: TabLayout.Tab?) {
        binding.apply {
            setVersionType(tab)
            version.setVersionType(versionType)
        }
    }

    private fun setVersionType(tab: TabLayout.Tab?) {
        versionType = when (tab) {
            release -> VersionType.RELEASE
            snapshot -> VersionType.SNAPSHOT
            beta -> VersionType.BETA
            alpha -> VersionType.ALPHA
            else -> VersionType.RELEASE
        }
    }

    private fun bindTab() {
        binding.apply {
            release = versionTab.newTab().setText(R.string.generic_release)
            snapshot = versionTab.newTab().setText(R.string.version_snapshot)
            beta = versionTab.newTab().setText(R.string.version_beta)
            alpha = versionTab.newTab().setText(R.string.version_alpha)

            versionTab.addTab(release!!)
            versionTab.addTab(snapshot!!)
            versionTab.addTab(beta!!)
            versionTab.addTab(alpha!!)

            versionTab.selectTab(release)
        }
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.versionLayout, Animations.BounceInDown))
            .apply(AnimPlayer.Entry(binding.operateLayout, Animations.BounceInLeft))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.versionLayout, Animations.FadeOutUp))
            .apply(AnimPlayer.Entry(binding.operateLayout, Animations.FadeOutRight))
    }
}
