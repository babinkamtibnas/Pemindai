package com.pemindai;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.NetworkRequest;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private TextView tvResultText;
    private LinearLayout resultContainer;
    private Button btnCopy, btnAction;
    private RecyclerView rvHistory;
    private HistoryAdapter historyAdapter;
    private DatabaseHelper dbHelper;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    handleScanResult(result.getContents());
                }
            });

    private final ActivityResultLauncher<String> getContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    decodeFromUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);

        tvResultText = findViewById(R.id.tv_result_text);
        resultContainer = findViewById(R.id.result_container);
        btnCopy = findViewById(R.id.btn_copy);
        btnAction = findViewById(R.id.btn_action);
        rvHistory = findViewById(R.id.rv_history);

        ImageButton btnThemeToggle = findViewById(R.id.btn_theme_toggle);
        btnThemeToggle.setOnClickListener(v -> {
            ThemeManager.toggleTheme(this);
            recreate();
        });

        findViewById(R.id.btn_scan_camera).setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            options.setPrompt(getString(R.string.scan_camera));
            options.setBeepEnabled(false);
            options.setBarcodeImageEnabled(true);
            barcodeLauncher.launch(options);
        });

        findViewById(R.id.btn_pick_image).setOnClickListener(v -> getContent.launch("image/*"));

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new HistoryAdapter(dbHelper.getAllHistory());
        rvHistory.setAdapter(historyAdapter);
    }

    private void handleScanResult(String text) {
        dbHelper.addHistory(text);
        historyAdapter.updateData(dbHelper.getAllHistory());

        resultContainer.setVisibility(View.VISIBLE);
        tvResultText.setText(text);

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("scanned_text", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });

        setupActionButton(text);
    }

    private void setupActionButton(String text) {
        btnAction.setVisibility(View.VISIBLE);
        if (text.startsWith("http://") || text.startsWith("https://")) {
            btnAction.setText(R.string.open_link);
            btnAction.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_link, 0, 0, 0);
            btnAction.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(text));
                startActivity(intent);
            });
        } else if (text.startsWith("WIFI:")) {
            btnAction.setText(R.string.connect_wifi);
            btnAction.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_wifi, 0, 0, 0);
            btnAction.setOnClickListener(v -> connectToWifi(text));
        } else {
            btnAction.setVisibility(View.GONE);
        }
    }

    private void connectToWifi(String wifiString) {
        // WIFI:S:SSID;T:WPA;P:PASSWORD;;
        String ssid = "";
        String password = "";
        Pattern ssidPattern = Pattern.compile("S:([^;]+);");
        Pattern passPattern = Pattern.compile("P:([^;]+);");
        Matcher ssidMatcher = ssidPattern.matcher(wifiString);
        Matcher passMatcher = passPattern.matcher(wifiString);

        if (ssidMatcher.find()) ssid = ssidMatcher.group(1);
        if (passMatcher.find()) password = passMatcher.group(1);

        if (ssid.isEmpty()) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build();

            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build();

            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.requestNetwork(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull android.net.Network network) {
                    super.onAvailable(network);
                    connectivityManager.bindProcessToNetwork(network);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.wifi_connecting, Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            WifiConfiguration wifiConfig = new WifiConfiguration();
            wifiConfig.SSID = String.format("\"%s\"", ssid);
            wifiConfig.preSharedKey = String.format("\"%s\"", password);

            int netId = wifiManager.addNetwork(wifiConfig);
            wifiManager.disconnect();
            wifiManager.enableNetwork(netId, true);
            wifiManager.reconnect();
            Toast.makeText(this, R.string.wifi_connecting, Toast.LENGTH_SHORT).show();
        }
    }

    private void decodeFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

            LuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            Result result = new MultiFormatReader().decode(binaryBitmap);
            handleScanResult(result.getText());
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_result, Toast.LENGTH_SHORT).show();
        }
    }
}
