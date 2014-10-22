package de.ph1b.audiobook.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import de.ph1b.audiobook.R;
import de.ph1b.audiobook.fragment.BookChooseFragment;
import de.ph1b.audiobook.utils.BookDetail;
import de.ph1b.audiobook.utils.CommonTasks;
import de.ph1b.audiobook.utils.DataBaseHelper;


public class MediaAdapter extends BaseAdapter {

    private final ArrayList<BookDetail> data;
    private final DataBaseHelper db;
    private final ArrayList<Integer> checkedBookIds = new ArrayList<Integer>();
    private final BookChooseFragment fragment;
    private boolean dragOn = true;

    public void toggleDrag(boolean dragOn) {
        this.dragOn = dragOn;
        Log.d("madapt", String.valueOf(dragOn) + "notify data now");
        notifyDataSetChanged();
    }


    public MediaAdapter(ArrayList<BookDetail> data, BookChooseFragment a) {
        this.data = data;
        this.fragment = a;
        db = DataBaseHelper.getInstance(fragment.getActivity());
    }

    public int getCount() {
        return data != null ? data.size() : 0;
    }

    public BookDetail getItem(int position) {
        return data.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public void setBookChecked(int bookId, Boolean checked) {
        if (checked)
            checkedBookIds.add(bookId);
        else
            checkedBookIds.remove(Integer.valueOf(bookId)); //integer value of to prevent accessing by position instead of object
    }

    public ArrayList<BookDetail> getCheckedBooks() {
        ArrayList<BookDetail> books = new ArrayList<BookDetail>();
        for (BookDetail b : data)
            for (Integer i : checkedBookIds)
                if (b.getId() == i)
                    books.add(b);
        if (books.size() > 0)
            return books;
        return null;
    }

    private void updateBookInData(BookDetail book) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getId() == book.getId()) {
                data.set(i, book);
            }
        }
    }


    public void unCheckAll() {
        checkedBookIds.clear();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;
        if (convertView == null) {
            LayoutInflater vi = (LayoutInflater) fragment.getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = vi.inflate(R.layout.media_chooser_listview, parent, false);

            viewHolder = new ViewHolder();
            viewHolder.iconImageView = (ImageView) convertView.findViewById(R.id.icon);
            viewHolder.textView = (TextView) convertView.findViewById(R.id.name);
            viewHolder.progressBar = (ProgressBar) convertView.findViewById(R.id.current_progress);
            viewHolder.dragger = (ImageView) convertView.findViewById(R.id.drag_handle);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        BookDetail b = data.get(position);

        //setting text
        String name = b.getName();
        viewHolder.textView.setText(name);

        viewHolder.iconImageView.setTag(b.getId());
        new LoadCoverAsync(b, viewHolder.iconImageView).execute();

        //setting bar
        viewHolder.progressBar.setMax(1000);
        viewHolder.progressBar.setTag(b.getId());
        new LoadProgressAsync(b, viewHolder.progressBar).execute();

        //setting drag visiblity
        if (dragOn && data.size() > 1) {
            viewHolder.dragger.setVisibility(View.VISIBLE);
        } else {
            //setting dragger invisible if toogled off or size is < 2
            viewHolder.dragger.setVisibility(View.GONE);
        }

        return convertView;
    }

    private class LoadProgressAsync extends AsyncTask<Void, Void, Integer> {
        WeakReference<ProgressBar> weakReference;
        BookDetail book;

        public LoadProgressAsync(BookDetail book, ProgressBar progressBar) {
            this.book = book;
            weakReference = new WeakReference<ProgressBar>(progressBar);
        }

        @Override
        protected void onPreExecute() {
            ProgressBar progressBar = weakReference.get();
            if (progressBar != null)
                progressBar.setProgress(0);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            return db.getGlobalProgress(book);
        }

        @Override
        protected void onPostExecute(Integer globalProgress) {
            ProgressBar progressBar = weakReference.get();
            if (progressBar != null && (book.getId() == progressBar.getTag())) {
                progressBar.setProgress(globalProgress);
            }
        }
    }

    private class LoadCoverAsync extends AsyncTask<Void, Void, Bitmap> {

        private WeakReference<ImageView> weakReference;
        private BookDetail book;

        public LoadCoverAsync(BookDetail book, ImageView imageView) {
            this.book = book;
            weakReference = new WeakReference<ImageView>(imageView);
        }

        @Override
        protected void onPreExecute() {
            ImageView imageView = weakReference.get();
            if (imageView != null) {
                imageView.setImageBitmap(null);
            }
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            String thumbPath = book.getThumb();
            if (thumbPath == null || thumbPath.equals("") || new File(thumbPath).isDirectory() || !(new File(thumbPath).exists())) {
                //if device is online try to load image from internet
                if (fragment.getActivity() != null && CommonTasks.isOnline(fragment.getActivity())) {
                    Context c = fragment.getActivity();
                    Bitmap bitmap = CommonTasks.genCoverFromInternet(book.getName(), 0, fragment.getActivity());
                    String imagePaths[] = CommonTasks.saveBitmap(bitmap, c);

                    if (imagePaths != null) {
                        book.setCover(imagePaths[0]);
                        book.setThumb(imagePaths[1]);
                        db.updateBook(book);
                        updateBookInData(book);
                        return BitmapFactory.decodeFile(imagePaths[1]);
                    }
                } else {
                    int thumbSize = CommonTasks.getThumbSize(fragment.getActivity());
                    return (CommonTasks.genCapital(book.getName(), thumbSize, fragment.getResources()));
                }
            } else {
                return BitmapFactory.decodeFile(thumbPath);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                ImageView imageView = weakReference.get();
                if (imageView != null && ((Integer) imageView.getTag() == book.getId())) {
                    imageView.setImageBitmap(bitmap);
                    if (fragment.getActivity() != null)
                        fragment.initPlayerWidget();
                }
            }
        }
    }


    static class ViewHolder {
        ImageView dragger;
        ImageView iconImageView;
        TextView textView;
        ProgressBar progressBar;
    }
}