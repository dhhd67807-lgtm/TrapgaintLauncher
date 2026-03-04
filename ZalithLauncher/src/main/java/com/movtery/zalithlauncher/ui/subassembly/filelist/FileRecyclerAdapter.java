package com.movtery.zalithlauncher.ui.subassembly.filelist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.databinding.ItemFileListViewBinding;
import com.movtery.zalithlauncher.utils.file.FileTools;
import com.movtery.zalithlauncher.utils.image.ImageUtils;
import com.movtery.zalithlauncher.utils.stringutils.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class FileRecyclerAdapter extends RecyclerView.Adapter<FileRecyclerAdapter.InnerHolder> {
    private final List<FileItemBean> mData = new ArrayList<>();
    private final List<FileItemBean> selectedFiles = new ArrayList<>();
    private boolean isMultiSelectMode = false;
    private boolean showDeleteButton = false;
    private OnItemClickListener mOnItemClickListener;
    private OnItemLongClickListener mOnItemLongClickListener;
    private OnMultiSelectListener mOnMultiSelectListener;
    private OnDeleteClickListener mOnDeleteClickListener;

    @NonNull
    @Override
    public InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new InnerHolder(ItemFileListViewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull InnerHolder holder, int position) {
        holder.setData(mData.get(position), position);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateItems(List<FileItemBean> items) {
        this.mData.clear();
        this.mData.addAll(items);
        notifyDataSetChanged();
    }

    public List<FileItemBean> getData() {
        return mData;
    }

    public boolean isNoFile() {
        return (mData.size() == 1 && !mData.get(0).isCanCheck) || mData.isEmpty();
    }

    private void toggleSelection(FileItemBean itemBean, CheckBox checkBox) {
        if (itemBean.isCanCheck) {
            if (selectedFiles.contains(itemBean)) {
                selectedFiles.remove(itemBean);
                checkBox.setChecked(false);
            } else {
                selectedFiles.add(itemBean);
                checkBox.setChecked(true);
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setMultiSelectMode(boolean multiSelectMode) {
        isMultiSelectMode = multiSelectMode;
        if (!multiSelectMode) {
            selectedFiles.clear(); // 退出多选模式时重置选择的文件
        }
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void selectAllFiles(boolean selectAll) {
        selectedFiles.clear();
        if (selectAll) { //全选时遍历全部item设置选择状态
            for (FileItemBean item : mData) {
                if (item.isCanCheck) {
                    selectedFiles.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public List<FileItemBean> getSelectedFiles() {
        return selectedFiles;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mOnItemClickListener = listener;
    }

    public void setOnMultiSelectListener(OnMultiSelectListener listener) {
        this.mOnMultiSelectListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.mOnItemLongClickListener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.mOnDeleteClickListener = listener;
    }

    public void setShowDeleteButton(boolean show) {
        this.showDeleteButton = show;
    }

    public interface OnItemClickListener {
        void onItemClick(int position, FileItemBean itemBean);
    }

    public interface OnMultiSelectListener {
        void onMultiSelect(List<FileItemBean> itemBeans);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(int position, FileItemBean itemBean);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(int position, FileItemBean itemBean);
    }

    public class InnerHolder extends RecyclerView.ViewHolder {
        private final Context context;
        private final ItemFileListViewBinding binding;
        private int mPosition;
        private FileItemBean mFileItemBean;

        public InnerHolder(@NonNull ItemFileListViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            context = itemView.getContext();

            binding.check.setOnClickListener(v -> {
                if (isMultiSelectMode) {
                    toggleSelection(mFileItemBean, binding.check);
                }
            });
            
            binding.deleteButton.setOnClickListener(v -> {
                if (mOnDeleteClickListener != null && !isMultiSelectMode) {
                    mOnDeleteClickListener.onDeleteClick(mPosition, mFileItemBean);
                }
            });
            
            if (mOnItemClickListener != null) {
                itemView.setOnClickListener(v -> {
                    if (isMultiSelectMode) {
                        toggleSelection(mFileItemBean, binding.check);
                    } else {
                        mOnItemClickListener.onItemClick(mPosition, mFileItemBean);
                    }
                });
            }
            itemView.setOnLongClickListener(v -> {
                if (isMultiSelectMode) {
                    if (mOnMultiSelectListener != null)
                        mOnMultiSelectListener.onMultiSelect(getSelectedFiles());
                } else {
                    if (mOnItemLongClickListener != null)
                        mOnItemLongClickListener.onItemLongClick(mPosition, mFileItemBean);
                }
                return true;
            });
        }

        public void setData(FileItemBean fileItemBean, int position) {
            mPosition = position;
            mFileItemBean = fileItemBean;
            File file = fileItemBean.file;

            binding.name.setText(fileItemBean.name);

            int infoLayoutVisible = View.GONE;
            if (fileItemBean.date != null) {
                String date = StringUtils.formatDate(fileItemBean.date, Locale.getDefault(), TimeZone.getDefault());
                binding.time.setText(date);
                binding.time.setVisibility(View.VISIBLE);
                infoLayoutVisible = View.VISIBLE;
            } else binding.time.setVisibility(View.GONE);

            if (fileItemBean.size != null) {
                String size = FileTools.formatFileSize(fileItemBean.size);
                binding.size.setText(size);
                binding.size.setVisibility(View.VISIBLE);
                infoLayoutVisible = View.VISIBLE;
            } else binding.size.setVisibility(View.GONE);

            binding.infoLayout.setVisibility(infoLayoutVisible);

            if (fileItemBean.isHighlighted) {
                binding.name.setTextColor(Color.rgb(69, 179, 162)); //设置高亮
            } else {
                binding.name.setTextColor(binding.name.getResources().getColor(R.color.black_or_white, binding.name.getContext().getTheme()));
            }

            if (fileItemBean.isCanCheck) {
                binding.check.setVisibility(isMultiSelectMode ? View.VISIBLE : View.GONE);
                binding.check.setChecked(selectedFiles.contains(fileItemBean));
                binding.deleteButton.setVisibility((showDeleteButton && !isMultiSelectMode) ? View.VISIBLE : View.GONE);
            } else {
                binding.check.setVisibility(View.GONE);
                binding.deleteButton.setVisibility(View.GONE);
            }
            
            // Check if this version is installed (for version lists only)
            if (file == null && fileItemBean.name != null) {
                // This is a version item (not a file), check if it's installed
                File versionDir = new File(com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome.getVersionsHome(), fileItemBean.name);
                boolean isInstalled = versionDir.exists() && versionDir.isDirectory();
                binding.installedCheck.setVisibility(isInstalled ? View.VISIBLE : View.GONE);
            } else {
                binding.installedCheck.setVisibility(View.GONE);
            }

            if (file != null && file.isFile() && ImageUtils.isImage(file)) {
                Glide.with(context).load(file)
                        .override(binding.image.getWidth(), binding.image.getHeight())
                        .centerCrop()
                        .into(new DrawableImageViewTarget(binding.image));
            } else {
                binding.image.setImageDrawable(fileItemBean.image);
            }
        }
    }
}
