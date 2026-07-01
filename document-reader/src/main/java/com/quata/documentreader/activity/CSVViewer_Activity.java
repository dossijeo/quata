package com.quata.documentreader.activity;

import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.view.View;

import com.quata.documentreader.DocumentReaderChrome;
import com.quata.documentreader.adapters_All.TableEventListener;
import com.quata.documentreader.adapters_All.TablePreviewwAdp;
import com.quata.documentreader.R;
import com.quata.documentreader.dataType.Cell;
import com.quata.documentreader.dataType.ColumnHeader;
import com.quata.documentreader.dataType.RowHeader;
import com.quata.documentreader.databinding.ActivityCsvViewerBinding;
import com.quata.documentreader.manageui.CustomFrameLayout;
import com.quata.documentreader.util.CSVReader;
import com.quata.documentreader.util.Utility;
import com.quata.documentreader.widgets.tableview.TableView;
import com.quata.documentreader.widgets.tableview.filter.Filter;
import com.quata.documentreader.widgets.tableview.pagination.Pagination;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CSVViewer_Activity extends BaseActivity {

    ActivityCsvViewerBinding binding;
    private CustomFrameLayout.Builder toolbarBuilder;
    List<List<Cell>> cellDataList = new ArrayList<>();
    List<ColumnHeader> columnHeaderList = new ArrayList<>();
    ArrayList<List<String>> csv_data = new ArrayList<>();
    Boolean fromConverterApp = false;
    private Pagination mPagination;
    private final boolean mPaginationEnabled = false;
    private Filter mTableFilter;
    private TableView mTableView;
    private String filePath;
    private String fileName;
    private final ExecutorService csvExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Future<?> csvLoadFuture;
    List<RowHeader> rowHeaderList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityCsvViewerBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        this.mTableView = findViewById(R.id.tableview);
        if (getIntent() != null) {
            String stringExtra = getIntent().getStringExtra("path");
            String stringExtra2 = getIntent().getStringExtra("name");
            this.filePath = stringExtra;
            this.fileName = stringExtra2;
            this.fromConverterApp = getIntent().getBooleanExtra("fromConverterApp", false);
            Integer.parseInt(getIntent().getStringExtra("fileType"));
            loadCsvDataAsync(stringExtra);
        }
        DocumentReaderChrome.configureHeader(
                this,
                binding.activityRoot,
                binding.headerTitleText,
                binding.imgBack,
                binding.imgPrint,
                binding.imgOption,
                this.fileName,
                this.filePath
        );
        binding.imgPrint.setOnClickListener(v -> DocumentReaderChrome.printCsvRows(this, this.fileName, this.csv_data));
        this.mTableFilter = new Filter(this.mTableView);
        if (this.mPaginationEnabled) {
            this.mPagination = new Pagination(this.mTableView);
        }
    }

    private void loadCsvDataAsync(String path) {
        binding.progressBar.setVisibility(View.VISIBLE);
        this.mTableView.setVisibility(View.INVISIBLE);
        csvLoadFuture = csvExecutor.submit(() -> {
            loadCVSDate(path);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                binding.progressBar.setVisibility(View.GONE);
                CSVViewer_Activity.this.mTableView.setVisibility(View.VISIBLE);
                TablePreviewwAdp tablePreviewwAdp = new TablePreviewwAdp();
                CSVViewer_Activity.this.mTableView.setAdapter(tablePreviewwAdp);
                CSVViewer_Activity.this.mTableView.setTableViewListener(new TableEventListener(CSVViewer_Activity.this.mTableView));
                tablePreviewwAdp.setAllItems(CSVViewer_Activity.this.columnHeaderList, CSVViewer_Activity.this.rowHeaderList, CSVViewer_Activity.this.cellDataList);
            });
        });
    }

    public void loadCVSDate(String str) {
        try {
            List read = new CSVReader(this).read(new FileInputStream(str));
            if (read.size() > 0) {
                for (int i = 0; i < read.size(); i++) {
                    String[] strArr = (String[]) read.get(i);
                    ArrayList<Cell> arrayList = new ArrayList<>();
                    ArrayList<String> arrayList2 = new ArrayList<>();
                    if (strArr.length > 1) {
                        String str2 = "";
                        for (int i2 = 0; i2 < strArr.length; i2++) {
                            if (i == 0) {
                                this.columnHeaderList.add(new ColumnHeader(String.valueOf(i), strArr[i2]));
                            } else {
                                arrayList.add(new Cell(i2 + "-" + i, strArr[i2]));
                            }
                            arrayList2.add(strArr[i2] + "");
                            str2 = str2 + " -  " + strArr[i2];
                        }
                        this.rowHeaderList.add(new RowHeader(String.valueOf(i), String.valueOf(i)));
                    }
                    this.cellDataList.add(arrayList);
                    this.csv_data.add(arrayList2);
                }
                Utility.logCatMsg("size " + read.size());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    public void filterTable(String str) {
        this.mTableFilter.set(str);
    }

    public void filterTableForMood(String str) {
        this.mTableFilter.set(2, str);
    }

    public void filterTableForGender(String str) {
        this.mTableFilter.set(4, str);
    }

    public void nextTablePage() {
        this.mPagination.nextPage();
    }

    public void previousTablePage() {
        this.mPagination.previousPage();
    }

    public void goToTablePage(int i) {
        this.mPagination.goToPage(i);
    }

    public void setTableItemsPerPage(int i) {
        this.mPagination.setItemsPerPage(i);
    }

    @Override
    protected void onDestroy() {
        if (csvLoadFuture != null) {
            csvLoadFuture.cancel(true);
            csvLoadFuture = null;
        }
        csvExecutor.shutdownNow();
        super.onDestroy();
    }

}
