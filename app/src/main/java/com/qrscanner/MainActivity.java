package com.qrscanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;
import com.qrscanner.databinding.ActivityMainBinding;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ScanRecordAdapter adapter;
    private List<ScanRecord> records = new ArrayList<>();
    private static final int CAMERA_PERMISSION_CODE = 100;
    private boolean isContinuousMode = true;

    // ZXing launcher
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> scanLauncher =
        registerForActivityResult(new ScanContract(), this::handleScanResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupRecyclerView();
        setupButtons();
        updateCounter();
    }

    private void setupRecyclerView() {
        adapter = new ScanRecordAdapter(records, this::onDeleteRecord, this::onEditRemark);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.btnScan.setOnClickListener(v -> checkCameraAndScan());
        binding.btnExport.setOnClickListener(v -> exportToExcel());
        binding.btnClear.setOnClickListener(v -> showClearConfirmDialog());
    }

    private void checkCameraAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            startScan();
        }
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("对准二维码，自动识别");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setBarcodeImageEnabled(false);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        scanLauncher.launch(options);
    }

    private void handleScanResult(ScanIntentResult result) {
        if (result.getContents() != null) {
            String content = result.getContents();
            addRecord(content, "");

            // 持续扫码：自动重新打开扫描
            if (isContinuousMode) {
                binding.recyclerView.postDelayed(this::startScan, 300);
            }
        }
    }

    private void addRecord(String content, String remark) {
        int seq = records.size() + 1;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        ScanRecord record = new ScanRecord(seq, content, time, remark);
        records.add(0, record); // 最新的放最上面
        // 重新编号
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setSeq(records.size() - i);
        }
        adapter.notifyDataSetChanged();
        updateCounter();
        binding.tvEmpty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void onDeleteRecord(int position) {
        new AlertDialog.Builder(this)
            .setTitle("删除记录")
            .setMessage("确认删除这条扫码记录？")
            .setPositiveButton("删除", (d, w) -> {
                records.remove(position);
                for (int i = 0; i < records.size(); i++) {
                    records.get(i).setSeq(records.size() - i);
                }
                adapter.notifyDataSetChanged();
                updateCounter();
                if (records.isEmpty()) {
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void onEditRemark(int position) {
        ScanRecord record = records.get(position);
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_remark, null);
        EditText etRemark = dialogView.findViewById(R.id.etRemark);
        etRemark.setText(record.getRemark());

        new AlertDialog.Builder(this)
            .setTitle("编辑备注")
            .setView(dialogView)
            .setPositiveButton("保存", (d, w) -> {
                record.setRemark(etRemark.getText().toString().trim());
                adapter.notifyItemChanged(position);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showClearConfirmDialog() {
        if (records.isEmpty()) {
            Toast.makeText(this, "暂无记录", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("清空记录")
            .setMessage("确认清空全部 " + records.size() + " 条扫码记录？")
            .setPositiveButton("清空", (d, w) -> {
                records.clear();
                adapter.notifyDataSetChanged();
                updateCounter();
                binding.tvEmpty.setVisibility(View.VISIBLE);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void updateCounter() {
        binding.tvCounter.setText("已扫: " + records.size() + " 条");
        binding.btnExport.setEnabled(!records.isEmpty());
        binding.btnClear.setEnabled(!records.isEmpty());
    }

    private void exportToExcel() {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("扫码记录");

            // 列宽
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 50 * 256);
            sheet.setColumnWidth(2, 22 * 256);
            sheet.setColumnWidth(3, 30 * 256);

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // 数据行样式（交替色）
            CellStyle evenStyle = workbook.createCellStyle();
            evenStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            evenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            evenStyle.setBorderBottom(BorderStyle.THIN);
            evenStyle.setBorderTop(BorderStyle.THIN);
            evenStyle.setBorderLeft(BorderStyle.THIN);
            evenStyle.setBorderRight(BorderStyle.THIN);

            CellStyle oddStyle = workbook.createCellStyle();
            oddStyle.setBorderBottom(BorderStyle.THIN);
            oddStyle.setBorderTop(BorderStyle.THIN);
            oddStyle.setBorderLeft(BorderStyle.THIN);
            oddStyle.setBorderRight(BorderStyle.THIN);

            // 表头行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"序号", "二维码内容", "扫描时间", "备注/标签"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            headerRow.setHeight((short) 500);

            // 数据行（按序号正序）
            List<ScanRecord> sortedRecords = new ArrayList<>(records);
            sortedRecords.sort((a, b) -> a.getSeq() - b.getSeq());
            for (int i = 0; i < sortedRecords.size(); i++) {
                ScanRecord record = sortedRecords.get(i);
                Row row = sheet.createRow(i + 1);
                CellStyle rowStyle = (i % 2 == 0) ? oddStyle : evenStyle;

                Cell c0 = row.createCell(0); c0.setCellValue(record.getSeq()); c0.setCellStyle(rowStyle);
                Cell c1 = row.createCell(1); c1.setCellValue(record.getContent()); c1.setCellStyle(rowStyle);
                Cell c2 = row.createCell(2); c2.setCellValue(record.getTime()); c2.setCellStyle(rowStyle);
                Cell c3 = row.createCell(3); c3.setCellValue(record.getRemark()); c3.setCellStyle(rowStyle);
                row.setHeight((short) 400);
            }

            // 摘要行
            Row summaryRow = sheet.createRow(sortedRecords.size() + 2);
            CellStyle summaryStyle = workbook.createCellStyle();
            Font summaryFont = workbook.createFont();
            summaryFont.setItalic(true);
            summaryStyle.setFont(summaryFont);
            Cell summaryCell = summaryRow.createCell(0);
            String exportTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            summaryCell.setCellValue("导出时间: " + exportTime + "  |  共 " + sortedRecords.size() + " 条记录");
            summaryCell.setCellStyle(summaryStyle);

            // 保存文件
            String fileName = "扫码记录_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".xlsx";
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
            workbook.close();

            showExportSuccess(file, fileName);

        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showExportSuccess(File file, String fileName) {
        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);

        new AlertDialog.Builder(this)
            .setTitle("✅ 导出成功")
            .setMessage("文件已保存:\n" + fileName + "\n\n共 " + records.size() + " 条记录")
            .setPositiveButton("分享文件", (d, w) -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "分享 Excel 文件"));
            })
            .setNegativeButton("确定", null)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
