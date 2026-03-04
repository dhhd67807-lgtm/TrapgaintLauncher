package com.movtery.zalithlauncher.ui.subassembly.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.ViewAccountBinding
import com.movtery.zalithlauncher.feature.accounts.AccountUtils
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.task.Task
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.ui.fragment.AccountFragment
import com.movtery.zalithlauncher.ui.fragment.FragmentWithAnim
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.path.PathManager
import com.movtery.zalithlauncher.utils.skin.SkinLoader
import net.kdt.pojavlaunch.Tools
import java.io.File
import java.io.FileOutputStream

class AccountViewWrapper(private val parentFragment: FragmentWithAnim? = null, val binding: ViewAccountBinding) {
    private val mContext: Context = binding.root.context
    private var skinPickerLauncher: ActivityResultLauncher<Intent>? = null
    private var currentAccount: net.kdt.pojavlaunch.value.MinecraftAccount? = null

    init {
        // Setup skin picker launcher if parent fragment is available
        if (parentFragment is Fragment) {
            skinPickerLauncher = parentFragment.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        handleSkinSelection(uri)
                    }
                }
            }
        }
        
        // Setup click listener to open account management
        binding.root.setOnClickListener {
            android.util.Log.d("AccountViewWrapper", "Account view clicked!")
            parentFragment?.let { fragment ->
                ZHTools.swapFragmentWithAnim(
                    fragment,
                    AccountFragment::class.java,
                    AccountFragment.TAG,
                    null
                )
            }
        }
    }
    
    fun changeSkin(account: net.kdt.pojavlaunch.value.MinecraftAccount) {
        currentAccount = account
        openSkinPicker()
    }
    
    private fun openSkinPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        
        try {
            skinPickerLauncher?.launch(Intent.createChooser(intent, "Select Skin"))
        } catch (e: Exception) {
            Toast.makeText(mContext, "Failed to open file picker", Toast.LENGTH_SHORT).show()
            Logging.e("AccountViewWrapper", "Failed to open skin picker", e)
        }
    }
    
    private fun handleSkinSelection(uri: Uri) {
        val account = currentAccount ?: return
        
        Task.runTask {
            try {
                val skinFile = File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".png")
                skinFile.parentFile?.apply {
                    if (!exists()) mkdirs()
                }
                
                mContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(skinFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                Logging.i("AccountViewWrapper", "Skin updated successfully")
            } catch (e: Exception) {
                Logging.e("AccountViewWrapper", "Failed to save skin", e)
                throw e
            }
        }.ended(TaskExecutors.getAndroidUI()) {
            Toast.makeText(mContext, "Skin updated successfully!", Toast.LENGTH_SHORT).show()
            // Notify other components that account was updated
            org.greenrobot.eventbus.EventBus.getDefault().post(
                com.movtery.zalithlauncher.event.single.AccountUpdateEvent()
            )
        }.onThrowable { e ->
            TaskExecutors.runInUIThread {
                TipDialog.Builder(mContext)
                    .setTitle(R.string.generic_error)
                    .setMessage("Failed to update skin: ${e.message}")
                    .setWarning()
                    .showDialog()
            }
        }.execute()
    }

    fun refreshAccountInfo() {
        binding.apply {
            // Always show Trapcode logo instead of user skin
            userIcon.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.trapcode))
            
            // Hide all text - only show logo
            userName.visibility = View.GONE
            accountType.visibility = View.GONE
        }
    }
}
