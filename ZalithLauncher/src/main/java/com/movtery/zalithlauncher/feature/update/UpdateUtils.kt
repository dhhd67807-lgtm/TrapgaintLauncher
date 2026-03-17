package com.movtery.zalithlauncher.feature.update

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.movtery.zalithlauncher.InfoDistributor
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.update.LauncherVersion.FileSize
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.AllSettings.Companion.ignoreUpdate
import com.movtery.zalithlauncher.task.TaskExecutors.Companion.runInUIThread
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.ui.dialog.UpdateDialog
import com.movtery.zalithlauncher.utils.path.PathManager
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.http.CallUtils
import com.movtery.zalithlauncher.utils.http.CallUtils.CallbackListener
import com.movtery.zalithlauncher.utils.http.NetworkUtils
import com.movtery.zalithlauncher.utils.path.UrlManager
import com.movtery.zalithlauncher.utils.stringutils.StringUtils
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.Tools
import okhttp3.Call
import okhttp3.Response
import org.apache.commons.io.FileUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class UpdateUtils {
    companion object {
        private const val RELEASE_SOURCE = "GitHub Release"
        private val ARCH_SUFFIXES = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

        @JvmField
        val sApkFile: File = File(PathManager.DIR_APP_CACHE, "cache.apk")
        private var LAST_UPDATE_CHECK_TIME: Long = 0

        /**
         * 启动软件的更新检测是5分钟的冷却，避免频繁检测导致Github限制访问
         * @param force 强制检测（用于设置内更新检测）
         */
        @JvmStatic
        fun checkDownloadedPackage(context: Context, force: Boolean, ignore: Boolean) {
            if (force && !NetworkUtils.isNetworkAvailable(context)) {
                Toast.makeText(context, context.getString(R.string.generic_no_network), Toast.LENGTH_SHORT).show()
                return
            }

            val isRelease = (ZHTools.isRelease() || ZHTools.isPreRelease()) && !ZHTools.isDebug()

            if (sApkFile.exists()) {
                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageArchiveInfo(sApkFile.absolutePath, 0)

                if (isRelease && packageInfo != null) {
                    val packageName = packageInfo.packageName
                    val versionCode = packageInfo.versionCode
                    val thisVersionCode = ZHTools.getVersionCode()

                    if (packageName == ZHTools.getPackageName() && versionCode > thisVersionCode) {
                        installApk(context, sApkFile)
                    } else {
                        FileUtils.deleteQuietly(sApkFile)
                    }
                } else {
                    FileUtils.deleteQuietly(sApkFile)
                }
            } else {
                if (isRelease && (force || checkCooling())) {
                    AllSettings.updateCheck.put(ZHTools.getCurrentTimeMillis()).save()
                    Logging.i("Check Update", "Checking new update!")

                    //如果安装包不存在，那么将自动获取更新
                    updateCheckerMainProgram(context, ignore)
                }
            }
        }

        private fun checkCooling(): Boolean {
            return ZHTools.getCurrentTimeMillis() - AllSettings.updateCheck.getValue() > 5 * 60 * 1000 //5分钟冷却
        }

        @Synchronized
        fun updateCheckerMainProgram(context: Context, ignore: Boolean) {
            if (ZHTools.getCurrentTimeMillis() - LAST_UPDATE_CHECK_TIME <= 5000) return
            LAST_UPDATE_CHECK_TIME = ZHTools.getCurrentTimeMillis()

            CallUtils(object : CallbackListener {
                override fun onFailure(call: Call?) {
                    if (!ignore) {
                        showFailToast(context, context.getString(R.string.update_fail))
                    }
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call?, response: Response?) {
                    if (!response!!.isSuccessful) {
                        if (!ignore) {
                            val messageRes = if (response.code == 404) {
                                StringUtils.insertSpace(context.getString(R.string.update_without), ZHTools.getVersionName())
                            } else {
                                context.getString(R.string.update_fail_code, response.code)
                            }
                            showFailToast(context, messageRes)
                        }
                        Logging.e("UpdateLauncher", "Unexpected code " + response.code)
                    } else {
                        try {
                            val launcherVersion = resolveLauncherVersion(response.body!!.string()) ?: run {
                                Logging.e("Check Update", "Unable to resolve release metadata from GitHub")
                                return
                            }

                            val versionName = launcherVersion.versionName
                            if (ignore && versionName == ignoreUpdate.getValue()) return  //忽略此版本

                            val versionCode = launcherVersion.versionCode
                            fun checkPreRelease(): Boolean {
                                return if (!launcherVersion.isPreRelease) true
                                else ZHTools.isPreRelease() || AllSettings.acceptPreReleaseUpdates.getValue()
                            }
                            if (checkPreRelease() && ZHTools.getVersionCode() < versionCode) {
                                runInUIThread {
                                    if (ignore) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.update_downloading_tip, RELEASE_SOURCE),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        UpdateLauncher(context, launcherVersion).start()
                                    } else {
                                        UpdateDialog(context, launcherVersion).show()
                                    }
                                }
                            } else if (!ignore) {
                                runInUIThread {
                                    val nowVersionName = ZHTools.getVersionName()
                                    runInUIThread {
                                        Toast.makeText(
                                            context,
                                            StringUtils.insertSpace(context.getString(R.string.update_without), nowVersionName),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Logging.e("Check Update", Tools.printToString(e))
                        }
                    }
                }
            }, UrlManager.URL_GITHUB_RELEASE_LATEST, null).enqueue()
        }

        private fun resolveLauncherVersion(rawResponse: String): LauncherVersion? {
            val jsonObject = JSONObject(rawResponse)

            if (jsonObject.has("content")) {
                val rawBase64 = jsonObject.getString("content")
                val rawJson = StringUtils.decodeBase64(rawBase64)
                return Tools.GLOBAL_GSON.fromJson(rawJson, LauncherVersion::class.java)
            }

            if (jsonObject.has("tag_name")) {
                return parseGitHubRelease(jsonObject)
            }
            return null
        }

        private fun parseGitHubRelease(releaseJson: JSONObject): LauncherVersion? {
            val assets = releaseJson.optJSONArray("assets") ?: return null
            val selectedAsset = selectBestAsset(assets) ?: return null

            val downloadUrl = selectedAsset.optString("browser_download_url")
            if (downloadUrl.isBlank()) return null

            val tagName = releaseJson.optString("tag_name")
            val releaseName = releaseJson.optString("name")
            val assetName = selectedAsset.optString("name")

            val versionCode = parseVersionCode(tagName, releaseName) ?: return null
            val versionName = parseVersionName(tagName, releaseName, assetName)

            val titleText = releaseName.ifBlank { "Update $versionName" }
            val descriptionText = releaseJson.optString("body").ifBlank { titleText }
            val assetSize = selectedAsset.optLong("size")

            return LauncherVersion(
                versionCode,
                versionName,
                LauncherVersion.WhatsNew(titleText, titleText, titleText),
                LauncherVersion.WhatsNew(descriptionText, descriptionText, descriptionText),
                releaseJson.optString("published_at"),
                FileSize(assetSize, assetSize, assetSize, assetSize, assetSize),
                releaseJson.optBoolean("prerelease", false),
                downloadUrl
            )
        }

        private fun selectBestAsset(assets: JSONArray): JSONObject? {
            val archModel = getArchModel()?.lowercase()
            var firstApk: JSONObject? = null
            var universalApk: JSONObject? = null

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name").lowercase()
                if (!name.endsWith(".apk")) continue

                if (firstApk == null) {
                    firstApk = asset
                }

                if (archModel != null && name.endsWith("-$archModel.apk")) {
                    return asset
                }

                if (universalApk == null && ARCH_SUFFIXES.none { name.endsWith("-$it.apk") }) {
                    universalApk = asset
                }
            }
            return universalApk ?: firstApk
        }

        private fun parseVersionCode(tagName: String, releaseName: String): Int? {
            fun parse(source: String): Int? {
                val cleaned = source.trim()
                    .removePrefix("v")
                    .removePrefix("V")
                return cleaned.toIntOrNull()
                    ?: cleaned.filter { it.isDigit() }.toIntOrNull()
            }
            return parse(tagName) ?: parse(releaseName)
        }

        private fun parseVersionName(tagName: String, releaseName: String, assetName: String): String {
            if (assetName.isNotBlank()) {
                val base = assetName.removeSuffix(".apk")
                val strippedArch = ARCH_SUFFIXES
                    .firstOrNull { base.endsWith("-$it") }
                    ?.let { base.removeSuffix("-$it") } ?: base
                val extracted = strippedArch.removePrefix("${InfoDistributor.LAUNCHER_NAME}-")
                if (extracted.isNotBlank() && extracted != strippedArch) return extracted
            }

            if (releaseName.isNotBlank()) {
                return releaseName
            }
            return tagName.removePrefix("v").removePrefix("V")
        }

        @JvmStatic
        fun showFailToast(context: Context, resString: String) {
            runInUIThread {
                Toast.makeText(context, resString, Toast.LENGTH_SHORT).show()
            }
        }

        @JvmStatic
        fun getArchModel(arch: Int = Tools.DEVICE_ARCHITECTURE): String? {
            if (arch == Architecture.ARCH_ARM64) return "arm64-v8a"
            if (arch == Architecture.ARCH_ARM) return "armeabi-v7a"
            if (arch == Architecture.ARCH_X86_64) return "x86_64"
            if (arch == Architecture.ARCH_X86) return "x86"
            return null
        }

        @JvmStatic
        fun getFileSize(fileSize: FileSize): Long {
            val arch = Tools.DEVICE_ARCHITECTURE
            if (arch == Architecture.ARCH_ARM64) return fileSize.arm64
            if (arch == Architecture.ARCH_ARM) return fileSize.arm
            if (arch == Architecture.ARCH_X86_64) return fileSize.x86_64
            if (arch == Architecture.ARCH_X86) return fileSize.x86
            return fileSize.all
        }

        @JvmStatic
        fun getDownloadUrl(launcherVersion: LauncherVersion): String {
            launcherVersion.downloadUrl?.takeIf { it.isNotBlank() }?.let { return it }

            val archModel = getArchModel()
            return "${UrlManager.URL_HOME}/releases/download/" +
                    "${launcherVersion.versionCode}/${InfoDistributor.LAUNCHER_NAME}-${launcherVersion.versionName}" +
                    "${(if (archModel != null) String.format("-%s", archModel) else "")}.apk"
        }

        @JvmStatic
        fun installApk(context: Context, outputFile: File) {
            runInUIThread {
                TipDialog.Builder(context)
                    .setTitle(R.string.update)
                    .setMessage(StringUtils.insertNewline(context.getString(R.string.update_success), outputFile.absolutePath))
                    .setCenterMessage(false)
                    .setCancelable(false)
                    .setConfirmClickListener {
                        //安装
                        val intent = Intent(Intent.ACTION_VIEW)
                        val apkUri = FileProvider.getUriForFile(context, context.packageName + ".provider", outputFile)
                        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(intent)
                    }.showDialog()
            }
        }
    }
}
