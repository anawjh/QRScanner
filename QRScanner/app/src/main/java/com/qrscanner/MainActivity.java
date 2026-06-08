package com.qrscanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
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

import org.apache.poi.ss.usermodel.*;
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

    // 扫码模式：false=摄像头，true=PDA硬件扫码枪
    private boolean isPdaMode = false;
    private StringBuilder pdaBuffer = new StringBuilder();
    private long lastKeyTime = 0;

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
        updateModeIndicator();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "📷 摄像头扫码模式");
        menu.add(0, 2, 0, "🔫 PDA激光扫码模式");
        menu.add(0, 3, 0, "📞 联系开发者");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                isPdaMode = false;
                updateModeIndicator();
                Toast.makeText(this, "已切换：摄像头扫码模式", Toast.LENGTH_SHORT).show();
                return true;
            case 2:
                isPdaMode = true;
                updateModeIndicator();
                showPdaModeGuide();
                return true;
            case 3:
                showContactDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateModeIndicator() {
        if (isPdaMode) {
            binding.btnScan.setText("🔫 PDA模式已启动");
            binding.btnScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF6A1B9A));
        } else {
            binding.btnScan.setText("📷 开始扫码");
            binding.btnScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF1976D2));
        }
    }

    private void showPdaModeGuide() {
        new AlertDialog.Builder(this)
            .setTitle("🔫 PDA激光扫码模式")
            .setMessage("PDA模式已启动！\n\n使用方法：\n• 直接按 PDA 扫码键扫描条码\n• 数据会自动录入，无需点击任何按钮\n• 支持连续快速扫码\n\n提示：请确保 App 界面处于前台运行状态")
            .setPositiveButton("知道了", null)
            .show();
    }

    private void showContactDialog() {
        new AlertDialog.Builder(this)
            .setTitle("联系开发者")
            .setMessage("jala批量扫码\n\n📱 联系电话：13528490965\n\n如需定制开发扫码项目，欢迎联系！")
            .setPositiveButton("拨打电话", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:13528490965"));
                startActivity(intent);
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    // PDA 硬件扫码枪通过 KeyEvent 输入数据
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isPdaMode) return super.onKeyDown(keyCode, event);

        long currentTime = System.currentTimeMillis();
        // 超过500ms没有输入，清空缓冲区（新一次扫码）
        if (currentTime - lastKeyTime > 500) {
            pdaBuffer.setLength(0);
        }
        lastKeyTime = currentTime;

        // Enter键 = 扫码完成
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            String scanned = pdaBuffer.toString().trim();
            if (!scanned.isEmpty()) {
                addRecord(scanned, "");
                pdaBuffer.setLength(0);
            }
            return true;
        }

        // 普通字符累积
        char c = (char) event.getUnicodeChar();
        if (c != 0) {
            pdaBuffer.append(c);
        }
        return true;
    }

    private void setupRecyclerView() {
        adapter = new ScanRecordAdapter(records, this::onDeleteRecord, this::onEditRemark);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.btnScan.setOnClickListener(v -> {
            if (isPdaMode) {
                Toast.makeText(this, "PDA模式：直接按扫码键即可", Toast.LENGTH_SHORT).show();
            } else {
                checkCameraAndScan();
            }
        });
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
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        scanLauncher.launch(options);
    }

    private void handleScanResult(ScanIntentResult result) {
        if (result.getContents() != null) {
            addRecord(result.getContents(), "");
            if (!isPdaMode) {
                binding.recyclerView.postDelayed(this::startScan, 300);
            }
        }
    }

    private void addRecord(String content, String remark) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        ScanRecord record = new ScanRecord(records.size() + 1, content, time, remark);
        records.add(0, record);
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setSeq(records.size() - i);
        }
        adapter.notifyDataSetChanged();
        updateCounter();
        binding.tvEmpty.setVisibility(View.GONE);
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
                if (records.isEmpty()) binding.tvEmpty.setVisibility(View.VISIBLE);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void onEditRemark(int position) {
        ScanRecord record = records.get(position);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_remark, null);
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
        if (records.isEmpty()) { Toast.makeText(this, "暂无记录", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
            .setTitle("清空记录")
            .setMessage("确认清空全部 " + records.size() + " 条记录？")
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
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 50 * 256);
            sheet.setColumnWidth(2, 22 * 256);
            sheet.setColumnWidth(3, 30 * 256);

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

            CellStyle evenStyle = workbook.createCellStyle();
            evenStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            evenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            evenStyle.setBorderBottom(BorderStyle.THIN); evenStyle.setBorderTop(BorderStyle.THIN);
            evenStyle.setBorderLeft(BorderStyle.THIN); evenStyle.setBorderRight(BorderStyle.THIN);

            CellStyle oddStyle = workbook.createCellStyle();
            oddStyle.setBorderBottom(BorderStyle.THIN); oddStyle.setBorderTop(BorderStyle.THIN);
            oddStyle.setBorderLeft(BorderStyle.THIN); oddStyle.setBorderRight(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"序号", "二维码内容", "扫描时间", "备注/标签"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            headerRow.setHeight((short) 500);

            List<ScanRecord> sorted = new ArrayList<>(records);
            sorted.sort((a, b) -> a.getSeq() - b.getSeq());
            for (int i = 0; i < sorted.size(); i++) {
                ScanRecord r = sorted.get(i);
                Row row = sheet.createRow(i + 1);
                CellStyle s = (i % 2 == 0) ? oddStyle : evenStyle;
                Cell c0 = row.createCell(0); c0.setCellValue(r.getSeq()); c0.setCellStyle(s);
                Cell c1 = row.createCell(1); c1.setCellValue(r.getContent()); c1.setCellStyle(s);
                Cell c2 = row.createCell(2); c2.setCellValue(r.getTime()); c2.setCellStyle(s);
                Cell c3 = row.createCell(3); c3.setCellValue(r.getRemark()); c3.setCellStyle(s);
                row.setHeight((short) 400);
            }

            Row summaryRow = sheet.createRow(sorted.size() + 2);
            Cell sc = summaryRow.createCell(0);
            sc.setCellValue("导出时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(new Date()) + "  |  共 " + sorted.size() + " 条  |  jala批量扫码 13528490965");

            String fileName = "扫码记录_" + new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date()) + ".xlsx";
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) { workbook.write(fos); }
            workbook.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            new AlertDialog.Builder(this)
                .setTitle("✅ 导出成功")
                .setMessage("文件：" + fileName + "\n共 " + records.size() + " 条记录")
                .setPositiveButton("分享文件", (d, w) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "分享 Excel 文件"));
                })
                .setNegativeButton("确定", null)
                .show();

        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " +
e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan();
        }
    }
}
