package com.quata.documentreader.activity;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quata.documentreader.DocumentReaderChrome;
import com.quata.documentreader.QuataDocumentReaderTheme;
import com.quata.documentreader.R;

import java.io.File;
import java.io.IOException;

public class PDF_Reader_Activity extends AppCompatActivity {

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private String path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        QuataDocumentReaderTheme.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        path = getIntent().getStringExtra("path");
        String fileName = getIntent().getStringExtra("name");
        DocumentReaderChrome.configureHeader(
                this,
                findViewById(R.id.activityRoot),
                findViewById(R.id.header_title_text),
                findViewById(R.id.img_back),
                findViewById(R.id.img_print),
                findViewById(R.id.img_download),
                fileName,
                path
        );
        RecyclerView pages = findViewById(R.id.pdfPagesRecyclerView);

        pages.setLayoutManager(new LinearLayoutManager(this));
        pages.setHasFixedSize(false);

        try {
            File filepdf = new File(path);
            fileDescriptor = ParcelFileDescriptor.open(filepdf, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);
            pages.setAdapter(new PdfPageAdapter(pdfRenderer, getRenderWidthPx()));
        } catch (Exception exception) {
            Toast.makeText(this, R.string.quata_document_reader_unsupported, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pdfRenderer != null) {
            pdfRenderer.close();
            pdfRenderer = null;
        }
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (IOException ignored) {
            }
            fileDescriptor = null;
        }
    }

    private int getRenderWidthPx() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int horizontalPaddingPx = (int) (24 * metrics.density);
        return Math.max(1, metrics.widthPixels - horizontalPaddingPx);
    }

    private static final class PdfPageAdapter extends RecyclerView.Adapter<PdfPageViewHolder> {
        private final PdfRenderer renderer;
        private final int renderWidthPx;

        PdfPageAdapter(PdfRenderer renderer, int renderWidthPx) {
            this.renderer = renderer;
            this.renderWidthPx = renderWidthPx;
        }

        @NonNull
        @Override
        public PdfPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf_page, parent, false);
            return new PdfPageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PdfPageViewHolder holder, int position) {
            holder.clearBitmap();
            holder.pageNumber.setText((position + 1) + " / " + getItemCount());

            PdfRenderer.Page page = renderer.openPage(position);
            try {
                int renderHeightPx = Math.max(1, Math.round(renderWidthPx * (page.getHeight() / (float) page.getWidth())));
                Bitmap bitmap = Bitmap.createBitmap(renderWidthPx, renderHeightPx, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                holder.setBitmap(bitmap);
            } finally {
                page.close();
            }
        }

        @Override
        public int getItemCount() {
            return renderer.getPageCount();
        }

        @Override
        public void onViewRecycled(@NonNull PdfPageViewHolder holder) {
            holder.clearBitmap();
            super.onViewRecycled(holder);
        }
    }

    private static final class PdfPageViewHolder extends RecyclerView.ViewHolder {
        private final TextView pageNumber;
        private final ImageView pageImage;
        private Bitmap bitmap;

        PdfPageViewHolder(@NonNull View itemView) {
            super(itemView);
            pageNumber = itemView.findViewById(R.id.pdfPageNumber);
            pageImage = itemView.findViewById(R.id.pdfPageImage);
        }

        void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            pageImage.setImageBitmap(bitmap);
        }

        void clearBitmap() {
            pageImage.setImageDrawable(null);
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
        }
    }
}
