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
        updateModeUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "📷 摄像头扫码模式 Camera");
        menu.add(0, 2, 0, "🔫 PDA 激光扫码模式");
        menu.add(0, 3, 0, "📞 联系开发者 13528490965");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                isPdaMode = false;
                updateModeUI();
                Toast.makeText(this, "✅ 已切换：摄像头扫码模式", Toast.LENGTH_SHORT).show();
                return true;
            case 2:
                isPdaMode = true;
                updateModeUI();
                new AlertDialog.Builder(this)
                    .setTitle("🔫 PDA 激光扫码模式已启动")
                    .setMessage("直接按 PDA 扫码键扫描条码\n数据会自动录入，无需点击按钮\n支持连续快速扫码")
                    .setPositiveButton("知道了", null)
                    .show();
                return true;
            case 3:
                new AlertDialog.Builder(this)
                    .setTitle("联系开发者")
                    .setMessage("jala 批量扫码工具\n\n📱 13528490965\n\n如需定制开发扫码项目，欢迎联系！")
                    .setPositiveButton("拨打电话", (d, w) ->
                        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:13528490965"))))
                    .setNegativeButton("关闭", null)
                    .show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateModeUI() {
        if (isPdaMode) {
            binding.btnScan.setText("🔫 PDA 模式已启动，直接扫码");
            binding.btnScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF6A1B9A));
            binding.tvModeBar.setText("🔫 当前模式：PDA 激光扫码  |  右上角菜单可切换摄像头模式");
            binding.tvModeBar.setBackgroundColor(0xFF6A1B9A);
        } else {
            binding.btnScan.setText("📷 开始扫码 Camera");
            binding.btnScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF1976D2));
            binding.tvModeBar.setText("📷 当前模式：摄像头扫码  |  右上角菜单可切换 PDA 模式");
            binding.tvModeBar.setBackgroundColor(0xFF1976D2);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isPdaMode) return super.onKeyDown(keyCode, event);
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastKeyTime > 500) pdaBuffer.setLength(0);
        lastKeyTime = currentTime;
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            String scanned = pdaBuffer.toString().trim();
            if (!scanned.isEmpty()) {
                addRecord(scanned, "");
                pdaBuffer.setLength(0);
            }
            return true;
        }
        char c = (char) event.getUnicodeChar();
        if (c != 0) pdaBuffer.append(c);
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
                Toast.makeText(this, "🔫 PDA 模式：直接按扫码键即可", Toast.LENGTH_SHORT).show();
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
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
