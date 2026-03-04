package com.movtery.zalithlauncher.ui.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentModsBinding
import com.movtery.zalithlauncher.feature.mod.ModToggleHandler
import com.movtery.zalithlauncher.feature.mod.ModUtils
import com.movtery.zalithlauncher.task.Task
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.FilesDialog
import com.movtery.zalithlauncher.ui.dialog.FilesDialog.FilesButton
import com.movtery.zalithlauncher.ui.subassembly.filelist.FileIcon
import com.movtery.zalithlauncher.ui.subassembly.filelist.FileItemBean
import com.movtery.zalithlauncher.ui.subassembly.filelist.FileSelectedListener
import com.movtery.zalithlauncher.ui.subassembly.view.SearchViewWrapper
import com.movtery.zalithlauncher.utils.NewbieGuideUtils
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.anim.AnimUtils.Companion.setVisibilityAnim
import com.movtery.zalithlauncher.utils.file.FileCopyHandler
import com.movtery.zalithlauncher.utils.file.FileTools
import com.movtery.zalithlauncher.utils.file.PasteFile
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import java.io.File
import java.util.function.Consumer

class ModsFragment : FragmentWithAnim(R.layout.fragment_mods) {
    companion object {
        const val TAG: String = "ModsFragment"
        const val BUNDLE_ROOT_PATH: String = "root_path"
    }

    private lateinit var binding: FragmentModsBinding
    private lateinit var mSearchViewWrapper: SearchViewWrapper
    private lateinit var mRootPath: String
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Any>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDocumentLauncher = registerForActivityResult(OpenDocumentWithExtension("jar", true)) { uris: List<Uri>? ->
            uris?.let { uriList ->
                val dialog = ZHTools.showTaskRunningDialog(requireContext())
                Task.runTask {
                    uriList.forEach { uri ->
                        FileTools.copyFileInBackground(requireContext(), uri, mRootPath)
                    }
                }.ended(TaskExecutors.getAndroidUI()) {
                    Toast.makeText(requireContext(), getString(R.string.profile_mods_added_mod), Toast.LENGTH_SHORT).show()
                    binding.fileRecyclerView.refreshPath()
                }.onThrowable { e ->
                    Tools.showErrorRemote(e)
                }.finallyTask(TaskExecutors.getAndroidUI()) {
                    dialog.dismiss()
                }.execute()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentModsBinding.inflate(layoutInflater)
        mSearchViewWrapper = SearchViewWrapper(this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initViews()
        parseBundle()

        binding.apply {
            fileRecyclerView.apply {
                setShowFiles(true)
                setShowFolders(false)
                setShowDeleteButton(true)

                setFileSelectedListener(object : FileSelectedListener() {
                    override fun onFileSelected(file: File?, path: String?) {
                        file?.let {
                            if (it.isFile) {
                                val fileName = it.name

                                val filesButton = FilesButton()
                                filesButton.setButtonVisibility(true, true, true, true, true,
                                    (fileName.endsWith(ModUtils.JAR_FILE_SUFFIX) || fileName.endsWith(ModUtils.DISABLE_JAR_FILE_SUFFIX)))
                                filesButton.setMessageText(if (it.isDirectory) getString(R.string.file_folder_message) else getString(R.string.file_message))

                                if (fileName.endsWith(ModUtils.JAR_FILE_SUFFIX)) filesButton.setMoreButtonText(getString(R.string.profile_mods_disable))
                                else if (fileName.endsWith(ModUtils.DISABLE_JAR_FILE_SUFFIX)) filesButton.setMoreButtonText(getString(R.string.profile_mods_enable))

                                val filesDialog = FilesDialog(requireContext(), filesButton,
                                    Task.runTask(TaskExecutors.getAndroidUI()) { refreshPath() },
                                    fullPath, it
                                )

                                filesDialog.setCopyButtonClick { visibility = View.VISIBLE }

                                //检测后缀名，以设置正确的按钮
                                if (fileName.endsWith(ModUtils.JAR_FILE_SUFFIX)) {
                                    filesDialog.setFileSuffix(ModUtils.JAR_FILE_SUFFIX)
                                    filesDialog.setMoreButtonClick {
                                        ModUtils.disableMod(it)
                                        refreshPath()
                                        filesDialog.dismiss()
                                    }
                                } else if (fileName.endsWith(ModUtils.DISABLE_JAR_FILE_SUFFIX)) {
                                    filesDialog.setFileSuffix(ModUtils.DISABLE_JAR_FILE_SUFFIX)
                                    filesDialog.setMoreButtonClick {
                                        ModUtils.enableMod(it)
                                        refreshPath()
                                        filesDialog.dismiss()
                                    }
                                }

                                filesDialog.show()
                            }
                        }
                    }

                    override fun onItemLongClick(file: File?, path: String?) {
                    }
                })

                setOnMultiSelectListener { itemBeans: List<FileItemBean> ->
                    if (itemBeans.isNotEmpty()) {
                        Task.runTask {
                            //取出全部文件
                            val selectedFiles: MutableList<File> = ArrayList()
                            itemBeans.forEach(Consumer { value: FileItemBean ->
                                val file = value.file
                                file?.apply { selectedFiles.add(this) }
                            })
                            selectedFiles
                        }.ended(TaskExecutors.getAndroidUI()) { selectedFiles ->
                            val filesButton = FilesButton()
                            filesButton.setButtonVisibility(true, true, false, false, true, true)
                            filesButton.setDialogText(
                                getString(R.string.file_multi_select_mode_title),
                                getString(R.string.file_multi_select_mode_message, itemBeans.size),
                                getString(R.string.profile_mods_disable_or_enable)
                            )

                            val filesDialog = FilesDialog(requireContext(), filesButton,
                                Task.runTask(TaskExecutors.getAndroidUI()) {
                                    closeMultiSelect()
                                    refreshPath()
                                }, fullPath, selectedFiles!!)
                            filesDialog.setMoreButtonClick {
                                ModToggleHandler(requireContext(), selectedFiles,
                                    Task.runTask(TaskExecutors.getAndroidUI()) {
                                        closeMultiSelect()
                                        refreshPath()
                                    }).start()
                            }
                            filesDialog.show()
                        }.execute()
                    }
                }

                setOnDeleteClickListener { position, itemBean ->
                    itemBean.file?.let { file ->
                        if (file.isFile) {
                            com.movtery.zalithlauncher.ui.dialog.TipDialog.Builder(requireContext())
                                .setTitle(R.string.generic_warning)
                                .setMessage(getString(R.string.file_delete))
                                .setWarning()
                                .setConfirmClickListener {
                                    if (file.delete()) {
                                        Toast.makeText(requireContext(), getString(R.string.file_added), Toast.LENGTH_SHORT).show()
                                        refreshPath()
                                    } else {
                                        Toast.makeText(requireContext(), getString(R.string.generic_error), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .showDialog()
                        }
                    }
                }

                setRefreshListener {
                    setVisibilityAnim(nothingLayout, isNoFile)
                }
            }

            goDownloadText.setOnClickListener{ goDownloadMod() }

            fileRecyclerView.lockAndListAt(File(mRootPath), File(mRootPath))
        }

        startNewbieGuide()
    }

    private fun startNewbieGuide() {
        // Newbie guide removed since sidebar is removed
    }

    private fun closeMultiSelect() {
        // Multi-select mode removed since sidebar is removed
    }

    private fun getFileSuffix(file: File): String {
        val name = file.name
        if (name.endsWith(ModUtils.DISABLE_JAR_FILE_SUFFIX)) {
            return ModUtils.DISABLE_JAR_FILE_SUFFIX
        } else if (name.endsWith(ModUtils.JAR_FILE_SUFFIX)) {
            return ModUtils.JAR_FILE_SUFFIX
        } else {
            val dotIndex = file.name.lastIndexOf('.')
            return if (dotIndex == -1) "" else file.name.substring(dotIndex)
        }
    }

    private fun goDownloadMod() {
        closeMultiSelect()
        ZHTools.swapFragmentWithAnim(
            this,
            DownloadFragment::class.java,
            DownloadFragment.TAG,
            null
        )
    }

    private fun parseBundle() {
        val bundle = arguments ?: throw NullPointerException("The argument is null!")
        mRootPath = bundle.getString(BUNDLE_ROOT_PATH) ?: throw IllegalStateException("root path is not set！")
    }

    private fun initViews() {
        binding.apply {
            mSearchViewWrapper.apply {
                setSearchListener(object : SearchViewWrapper.SearchListener {
                    override fun onSearch(string: String?, caseSensitive: Boolean): Int {
                        return fileRecyclerView.searchFiles(string, caseSensitive)
                    }
                })
                setShowSearchResultsListener(object : SearchViewWrapper.ShowSearchResultsListener {
                    override fun onSearch(show: Boolean) {
                        fileRecyclerView.setShowSearchResultsOnly(show)
                    }
                })
            }

            fileRecyclerView.setFileIcon(FileIcon.MOD)
        }
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        binding.apply {
            animPlayer.apply(AnimPlayer.Entry(modsLayout, Animations.BounceInDown))
        }
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        binding.apply {
            animPlayer.apply(AnimPlayer.Entry(modsLayout, Animations.FadeOutUp))
        }
    }
}

