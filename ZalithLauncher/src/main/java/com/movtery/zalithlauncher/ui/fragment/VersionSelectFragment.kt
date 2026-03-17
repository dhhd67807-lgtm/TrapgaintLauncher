package com.movtery.zalithlauncher.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentVersionSelectBinding
import com.movtery.zalithlauncher.event.value.InstallGameEvent
import com.movtery.zalithlauncher.feature.mod.modloader.OptiFineDownloadTask
import com.movtery.zalithlauncher.feature.version.install.Addon
import com.movtery.zalithlauncher.feature.version.install.InstallTaskItem
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.utils.anim.ViewAnimUtils
import com.movtery.zalithlauncher.utils.ZHTools
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.OptiFineUtils
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.Locale

data class MinecraftVersion(val version: String, val imageRes: Int)

class VersionSelectFragment : FragmentWithAnim(R.layout.fragment_version_select) {
    
    companion object {
        const val TAG = "VersionSelectFragment"
    }
    
    private lateinit var binding: FragmentVersionSelectBinding
    private var loaderType: String = "VANILLA"
    
    private val versions = listOf(
        MinecraftVersion("1.21", R.drawable.version_1_21),
        MinecraftVersion("1.20", R.drawable.version_1_20),
        MinecraftVersion("1.19", R.drawable.version_1_19),
        MinecraftVersion("1.18", R.drawable.version_1_18),
        MinecraftVersion("1.17", R.drawable.version_1_17),
        MinecraftVersion("1.16", R.drawable.version_1_16),
        MinecraftVersion("1.15", R.drawable.version_1_15),
        MinecraftVersion("1.14", R.drawable.version_1_14),
        MinecraftVersion("1.13", R.drawable.version_1_13),
        MinecraftVersion("1.12", R.drawable.version_1_12),
        MinecraftVersion("1.10", R.drawable.version_1_10),
        MinecraftVersion("1.9", R.drawable.version_1_9),
        MinecraftVersion("1.8", R.drawable.version_1_8)
    )
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVersionSelectBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get loader type from arguments
        loaderType = arguments?.getString("LOADER_TYPE") ?: "VANILLA"

        val displayVersions = if (loaderType == "CUSTOM") {
            versions.filter { it.version == "1.21" }
        } else {
            versions
        }
        
        binding.versionGrid.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.versionGrid.adapter = VersionAdapter(displayVersions, loaderType) { version ->
            // Open VersionSelectorFragment to show release/snapshot/beta/alpha
            val bundle = Bundle()
            bundle.putString("SELECTED_VERSION", version.version)
            bundle.putString("LOADER_TYPE", loaderType)
            ZHTools.swapFragmentWithAnim(
                this,
                VersionSelectorFragment::class.java,
                VersionSelectorFragment.TAG,
                bundle
            )
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh the adapter when returning to this fragment
        binding.versionGrid.adapter?.notifyDataSetChanged()
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
                optiFineVersions?.optifineVersions?.forEach { versionList ->
                    versionList.forEach { optiFineVersion ->
                        val cleanMcVersion = optiFineVersion.minecraftVersion.removePrefix("Minecraft").trim()
                        if (cleanMcVersion == mcVersion) {
                            mcOptiFineVersions.add(optiFineVersion)
                        }
                    }
                }
                
                if (mcOptiFineVersions.isEmpty()) {
                    // No OptiFine available, install vanilla only
                    // Post event with empty task map (vanilla only)
                    EventBus.getDefault().post(
                        InstallGameEvent(
                            mcVersion,
                            mcVersion,
                            emptyMap()
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
                            taskMap
                        )
                    )
                }
            } catch (e: Exception) {
                // Install vanilla as fallback
                EventBus.getDefault().post(
                    InstallGameEvent(
                        mcVersion,
                        mcVersion,
                        emptyMap()
                    )
                )
            }
        }
    }
    
    override fun slideIn(animPlayer: com.movtery.anim.AnimPlayer) {
        animPlayer.apply(com.movtery.anim.AnimPlayer.Entry(binding.versionGrid, Animations.BounceInDown))
    }
    
    override fun slideOut(animPlayer: com.movtery.anim.AnimPlayer) {
        animPlayer.apply(com.movtery.anim.AnimPlayer.Entry(binding.versionGrid, Animations.FadeOutUp))
    }
}

class VersionAdapter(
    private val versions: List<MinecraftVersion>,
    private val loaderType: String,
    private val onVersionClick: (MinecraftVersion) -> Unit
) : RecyclerView.Adapter<VersionAdapter.VersionViewHolder>() {
    
    class VersionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val versionImage: ImageView = view.findViewById(R.id.version_image)
        val versionText: TextView = view.findViewById(R.id.version_text)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_version_card, parent, false)
        return VersionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: VersionViewHolder, position: Int) {
        val version = versions[position]
        holder.versionImage.setImageResource(version.imageRes)
        holder.versionText.text = version.version
        
        // Check if any version starting with this version number is installed
        val hasInstalledVersion = checkIfVersionInstalled(version.version)
        
        // Apply grayscale if no version is installed
        if (!hasInstalledVersion) {
            val matrix = android.graphics.ColorMatrix()
            matrix.setSaturation(0f)
            val filter = android.graphics.ColorMatrixColorFilter(matrix)
            holder.versionImage.colorFilter = filter
            holder.versionImage.alpha = 0.5f
        } else {
            holder.versionImage.colorFilter = null
            holder.versionImage.alpha = 1.0f
        }
        
        holder.itemView.setOnClickListener {
            ViewAnimUtils.setViewAnim(holder.itemView, Animations.Pulse)
            onVersionClick(version)
        }
    }
    
    private fun checkIfVersionInstalled(versionPrefix: String): Boolean {
        val targetLoaderType = normalizeLoaderType(loaderType)
        val versions = com.movtery.zalithlauncher.feature.version.VersionsManager.getVersions()
        return versions.any { installedVersion ->
            val versionInfo = installedVersion.getVersionInfo()
            val mcVersion = versionInfo?.minecraftVersion ?: installedVersion.getVersionName()
            if (!mcVersion.startsWith(versionPrefix)) return@any false

            detectLoaderType(installedVersion) == targetLoaderType
        }
    }

    private fun detectLoaderType(version: com.movtery.zalithlauncher.feature.version.Version): String {
        val lowerVersionName = version.getVersionName().lowercase(Locale.ROOT)
        if (lowerVersionName.contains("dragon")) {
            return "CUSTOM"
        }

        val loaderName = version.getVersionInfo()
            ?.loaderInfo
            ?.firstOrNull()
            ?.name
        if (!loaderName.isNullOrBlank()) {
            return normalizeLoaderType(loaderName)
        }

        val versionNameType = normalizeLoaderType(version.getVersionName())
        if (versionNameType != "VANILLA") return versionNameType

        val versionJson = File(version.getVersionPath(), "${version.getVersionName()}.json")
        return runCatching {
            val json = versionJson.readText().uppercase(Locale.ROOT)
            when {
                json.contains("FABRIC") || json.contains("QUILT") -> "FABRIC"
                json.contains("FORGE") || json.contains("NEOFORGE") -> "FORGE"
                else -> "VANILLA"
            }
        }.getOrElse { "VANILLA" }
    }

    private fun normalizeLoaderType(rawLoaderType: String?): String {
        val normalized = rawLoaderType?.trim()?.uppercase(Locale.ROOT) ?: return "VANILLA"
        return when {
            normalized == "CUSTOM" || normalized.contains("DRAGON") -> "CUSTOM"
            normalized.contains("FABRIC") || normalized.contains("QUILT") -> "FABRIC"
            normalized.contains("FORGE") || normalized.contains("NEOFORGE") -> "FORGE"
            else -> "VANILLA"
        }
    }
    
    override fun getItemCount() = versions.size
}
