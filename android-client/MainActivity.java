package com.gajendra.ftp_client;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText editTextFTPServer;
    private EditText editTextUserName;
    private EditText editTextPassword;
    private TextView textViewStatus;
    private Button buttonConnect;
    private Button buttonListFiles, uploadFiles;

    private FTPClient ftpClient;
    private boolean isConnected = false;

    private ArrayAdapter<String> filesAdapter;
    private List<String> filesList;
    private ListView listViewFiles;

    private Uri selectedFileUri;  // Add this field to class
    private static final int PICK_FILE_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        editTextFTPServer = findViewById(R.id.editTextFTPServer);
        editTextUserName = findViewById(R.id.editTextUserName);
        editTextPassword = findViewById(R.id.editTextPassword);
        uploadFiles = findViewById(R.id.buttonUpload);
        textViewStatus = findViewById(R.id.textViewStatus);
        buttonConnect = findViewById(R.id.buttonConnect);
        buttonListFiles = findViewById(R.id.buttonListFiles);
        listViewFiles = findViewById(R.id.listViewFiles);

        // Default values matching Docker FTP server
        editTextFTPServer.setText("192.168.29.178");
        editTextUserName.setText("myuser");
        editTextPassword.setText("mypassword");

        ftpClient = new FTPClient();

        filesList = new ArrayList<>();
        filesAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                filesList);
        listViewFiles.setAdapter(filesAdapter);

        buttonConnect.setOnClickListener(v -> connectToFTPServer());

        buttonListFiles.setOnClickListener(v -> listFilesFromFTPServer());

//        uploadFiles.setOnClickListener(v -> uploadFileToFTPServer());

        // Add button click handler
        uploadFiles.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, 100);
        });

    }

    private void uploadUriToFTP() {
        if (!isConnected || selectedFileUri == null) {
            textViewStatus.setText("Connect first or pick file.");
            return;
        }

        textViewStatus.setText("Uploading...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                    ftpClient.enterLocalPassiveMode();

                    InputStream inputStream = getContentResolver().openInputStream(selectedFileUri);
                    String filename = getFileNameFromUri(selectedFileUri);

                    boolean success = ftpClient.storeFile(filename, inputStream);
                    inputStream.close();

                    final String result = success ? "Upload success: " + filename : "Upload failed";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textViewStatus.setText(result);
                        }
                    });
                } catch (final IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textViewStatus.setText("Upload error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        return cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

        private void connectToFTPServer() {
        final String host = editTextFTPServer.getText().toString().trim();
        final String user = editTextUserName.getText().toString().trim();
        final String pass = editTextPassword.getText().toString().trim();

        textViewStatus.setText("Connecting...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ftpClient.isConnected()) {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }

                    ftpClient.connect(host, 21);
                    boolean login = ftpClient.login(user, pass);

                    // Passive mode is important for Docker FTP
                    ftpClient.enterLocalPassiveMode();

                    isConnected = login;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isConnected) {
                                textViewStatus.setText("Connected to FTP server");
                            } else {
                                textViewStatus.setText("Login failed. Check credentials.");
                            }
                        }
                    });
                } catch (final IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textViewStatus.setText("Connection error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void listFilesFromFTPServer() {
        if (!isConnected) {
            textViewStatus.setText("Not connected. Connect first.");
            return;
        }

        textViewStatus.setText("Listing files...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // List files/directories in current directory
                    final FTPFile[] files = ftpClient.listFiles(); // core API call[web:19][web:25]

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            filesList.clear();

                            if (files != null && files.length > 0) {
                                for (FTPFile file : files) {
                                    String type = file.isDirectory() ? "[DIR]" : "[FILE]";
                                    String name = file.getName();
                                    long size = file.getSize();
                                    String display = type + " " + name + " (" + size + " bytes)";
                                    filesList.add(display);
                                }
                                textViewStatus.setText("Found " + files.length + " item(s).");
                            } else {
                                textViewStatus.setText("No files found in this directory.");
                            }

                            filesAdapter.notifyDataSetChanged();
                        }
                    });

                } catch (final IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textViewStatus.setText("Error listing files: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            selectedFileUri = data.getData();
            // Persist permission for reuse
            getContentResolver().takePersistableUriPermission(
                    selectedFileUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            uploadUriToFTP();  // Auto-upload
        }
    }
}
