package com.movtery.zalithlauncher.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.movtery.zalithlauncher.InfoCenter
import com.movtery.zalithlauncher.InfoDistributor
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.ActivitySplashBinding
import com.movtery.zalithlauncher.feature.unpack.Components
import com.movtery.zalithlauncher.feature.unpack.Jre
import com.movtery.zalithlauncher.feature.unpack.UnpackComponentsTask
import com.movtery.zalithlauncher.feature.unpack.UnpackJreTask
import com.movtery.zalithlauncher.feature.unpack.UnpackSingleFilesTask
import com.movtery.zalithlauncher.task.Task
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.utils.StoragePermissionsUtils
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.MissingStorageActivity
import net.kdt.pojavlaunch.Tools

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {
    private var isStarted: Boolean = false
    private lateinit var binding: ActivitySplashBinding
    private lateinit var installableAdapter: InstallableAdapter
    private val items: MutableList<InstallableItem> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initItems()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleText.text = InfoDistributor.APP_NAME
        
        // Apply gold to white gradient to "Dragon" text and make it bold
        applyGradientToTitle()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SplashActivity)
            adapter = installableAdapter
        }

        binding.startButton.apply {
            setOnClickListener {
                if (isStarted) return@setOnClickListener
                isStarted = true
                binding.splashText.setText(R.string.splash_screen_installing)
                installableAdapter.startAllTasks()
            }
            isClickable = false
        }

        if (!Tools.checkStorageRoot()) {
            startActivity(Intent(this, MissingStorageActivity::class.java))
            finish()
            return
        }

        //如果安卓版本小于等于9，则检查存储权限（不是管理所有文件权限），拥有存储权限会保证文件、文件夹正常创建
        //但是并不强制要求用户必须授予权限，如果用户拒绝，那么之后产生的问题将由用户承担
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !StoragePermissionsUtils.hasStoragePermissions(this)) {
            TipDialog.Builder(this)
                .setTitle(R.string.generic_warning)
                .setMessage(InfoCenter.replaceName(this, R.string.permissions_write_external_storage))
                .setWarning()
                .setConfirmClickListener { requestStoragePermissions() }
                .setCancelClickListener { checkEnd() } //用户取消，那就跟随用户的意愿
                .showDialog()
        } else {
            checkEnd()
        }
    }

    private fun requestStoragePermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            //无论用户是否授予了权限，都会完成检查，因为启动器并不强制要求权限
            //但是一旦因为存储权限出现了问题，那么将由用户自行承担后果
            checkEnd()
        }
    }

    private fun initItems() {
        Components.entries.forEach {
            val unpackComponentsTask = UnpackComponentsTask(this, it)
            if (!unpackComponentsTask.isCheckFailed()) {
                items.add(
                    InstallableItem(
                        it.displayName,
                        it.summary?.let { it1 -> getString(it1) },
                        unpackComponentsTask
                    )
                )
            }
        }
        Jre.entries.forEach {
            val unpackJreTask = UnpackJreTask(this, it)
            if (!unpackJreTask.isCheckFailed()) {
                items.add(
                    InstallableItem(
                        it.jreName,
                        getString(it.summary),
                        unpackJreTask
                    )
                )
            }
        }
        items.sort()
        installableAdapter = InstallableAdapter(items) {
            toMain()
        }
        
        // Set progress listener
        installableAdapter.setProgressListener { completed, total ->
            val percentage = if (total > 0) (completed * 100 / total) else 0
            runOnUiThread {
                binding.progressText.visibility = android.view.View.VISIBLE
                binding.progressText.text = "$percentage%"
            }
        }
    }
    
    private fun checkEnd() {
        installableAdapter.checkAllTask()
        Task.runTask {
            UnpackSingleFilesTask(this).run()
        }.execute()

        // Auto-install all components and skip to main screen
        if (items.isEmpty()) {
            // No components to install, go directly to main
            toMain()
        } else {
            // Auto-start installation without showing the list
            isStarted = true
            binding.splashText.setText(R.string.splash_screen_installing)
            binding.startButton.visibility = android.view.View.GONE
            binding.recyclerView.visibility = android.view.View.GONE
            installableAdapter.startAllTasks()
        }
    }

    private fun toMain() {
        startActivity(Intent(this, LauncherActivity::class.java))
        finish()
    }
    
    private fun applyGradientToTitle() {
        val titleText = InfoDistributor.APP_NAME
        val spannableString = android.text.SpannableString(titleText)
        
        // Find "Dragon" in the text
        val dragonIndex = titleText.indexOf("Dragon")
        if (dragonIndex != -1) {
            // Create gradient shader for "Dragon" text
            val paint = binding.titleText.paint
            val textWidth = paint.measureText("Dragon")
            
            val shader = android.graphics.LinearGradient(
                0f, 0f, textWidth, 0f,
                intArrayOf(
                    android.graphics.Color.parseColor("#FFD700"), // Gold
                    android.graphics.Color.parseColor("#FFFFFF")  // White
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            
            // Apply gradient to "Dragon" part
            spannableString.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FFD700")),
                dragonIndex,
                dragonIndex + 6,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Make entire text bold
            spannableString.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0,
                titleText.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        binding.titleText.text = spannableString
    }

    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE: Int = 100
    }
}