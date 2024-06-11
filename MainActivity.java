package com.example.map;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import androidx.annotation.NonNull;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.widget.Button;
import android.view.View;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.Fragment;
import com.example.map.Screenshot;
import android.Manifest;
import java.io.File;
import android.os.Environment;
import java.io.FileOutputStream;
import java.io.IOException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.media.MediaScannerConnection;


public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private ArrayList<LatLng> pathPoints = new ArrayList<>();
    private PolylineOptions polylineOptions;
    private Polyline polyline;
    private LatLng currentLatLng; // currentLatLng 필드를 추가
    private Circle circle; // circle 변수 추가
    private WeatherService weatherService; // WeatherService 인터페이스 변수 추가
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 100;

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 이미지를 표시할 이미지 뷰
        ImageView imageView = findViewById(R.id.imageView);

        // 내부 저장소에서 이미지 파일을 읽어옴
        File file = new File(getFilesDir(), "image.jpg");
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        //String fileName = file.getName(); // 파일 이름 추출

        // 이미지 뷰에 비트맵 설정
        imageView.setImageBitmap(bitmap);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        polylineOptions = new PolylineOptions()
                .width(5f)
                .color(Color.BLUE);

        // Retrofit 클라이언트 설정
        weatherService = ApiClient.getClient().create(WeatherService.class);

        // 위치 업데이트를 시작
        startLocationUpdates();

        // 캡쳐 버튼 클릭 이벤트 처리
        Button captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureMapScreen();
            }
        });
    }

    // 위치 업데이트를 시작
    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(3000); // 위치 업데이트 간격 (3초)
        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                // 새로운 위치가 업데이트되면 호출
                for (Location location : locationResult.getLocations()) {
                    // 새 위치를 Polyline에 추가
                    currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    pathPoints.add(currentLatLng); // 새 위치를 ArrayList에 추가
                    updatePath(); // 새로운 위치를 기반으로 Polyline을 업데이트

                    // 날씨 정보 가져오기
                    fetchWeatherData(location.getLatitude(), location.getLongitude());
                }
            }
        };
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 위치 권한이 없는 경우 권한 요청
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    // 날씨 데이터를 가져오는 메서드
    private void fetchWeatherData(double latitude, double longitude) {
        Call<WeatherResponse> call = weatherService.getCurrentWeather(latitude, longitude, "74c93cae5cff41bf786cc58d65a14aa6", "metric");
        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    WeatherResponse weatherResponse = response.body();
                    float temperature = weatherResponse.getMain().getTemp(); // 온도 가져오기
                    String description = weatherResponse.getWeather().get(0).getDescription();
                    // 날씨 정보를 기반으로 산책 여부 판단
                    String recommendation = getWalkRecommendation(temperature, description);
                    // UI 업데이트
                    updateWeatherUI(temperature, description, recommendation);
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                // API 호출 실패 처리
            }
        });
    }

    // 날씨에 따른 산책 여부를 판단하는 메서드
    private String getWalkRecommendation(float temperature, String description) {
        // 날씨 정보를 기반으로 산책 여부를 판단하여 반환
        if (description.contains("rain") || description.contains("snow")) {
            return "오늘은 산책하기에 좋지 않은 날씨입니다.";
        }

        // 온도를 기준으로 산책 여부를 판단하여 반환
        if (temperature >= 13 && temperature <= 20) {
            return "위험하지 않습니다. 신나게 야외 활동을 즐기세요!";
        } else if (temperature >= 21 && temperature <= 23) {
            return "위험의 가능성이 적습니다. 신나게 야외활동을 하되, 조심하세요.";
        } else if (temperature >= 24 && temperature <= 28) {
            return "견종에 따라 위험의 소지가 있습니다. 야외 활동을 할 때 강아지를 잘 지켜봐 주세요.";
        } else if (temperature >= 29 && temperature <= 31) {
            return "위험할 수 있습니다. 조심하세요.";
        } else if (temperature >= 32) {
            return "위험한 더위입니다. 오랜 시간 야외 활동을 하지 마세요.";
        } else {
            return "오늘은 산책하기에 좋은 날씨입니다!";
        }

    }

    // UI 업데이트 메서드
    private void updateWeatherUI(float temperature, String description, String recommendation) {
        // 날씨 정보 및 산책 여부를 UI에 업데이트
        TextView temperatureTextView = findViewById(R.id.temperatureTextView);
        TextView descriptionTextView = findViewById(R.id.descriptionTextView);
        TextView recommendationTextView = findViewById(R.id.recommendationTextView);

        // 온도와 날씨 설명을 화면에 표시
        temperatureTextView.setText("온도: " + temperature + "°C");
        descriptionTextView.setText("날씨: " + description);

        // 산책 여부 추천을 화면에 표시
        recommendationTextView.setText(recommendation);
    }

    // Polyline을 업데이트하여 이동 경로를 그림
    private void updatePath() {
        if (googleMap != null) {
            // Polyline을 초기화
            if (polyline != null) {
                polyline.remove();
            }
            // 새로운 Polyline을 그림
            polyline = googleMap.addPolyline(polylineOptions.addAll(pathPoints));
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;

        PolylineOptions polylineOptions = new PolylineOptions()
                .width(5f)
                .color(Color.BLUE);
        Polyline polyline = googleMap.addPolyline(polylineOptions);

        // 36.298211, 127.375827
        LatLng latLng = new LatLng(36.298211, 127.375827);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
        MarkerOptions markerOptions = new MarkerOptions().position(latLng).title("초록마을5단지");
        googleMap.addMarker(markerOptions);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 현재 위치 버튼 활성화
            googleMap.setMyLocationEnabled(true);
            // 지도 패딩 설정하여 현재 위치 버튼 위치 조정
            googleMap.setPadding(10, 10, 10, 10); // left, top, right, bottom
        } else {
            checkLocationPermissionWithRationale();
        }
    }

    private void checkLocationPermissionWithRationale() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle("위치정보")
                        .setMessage("이 앱을 사용하기 위해서는 위치정보에 접근이 필요합니다. 위치정보 접근을 허용하여 주세요.")
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        }).create().show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.option1) {
            drawCircleWithRadius(currentLatLng, 100); // 100m 반경으로 원 그리기
            return true;
        } else if (id == R.id.option2) {
            drawCircleWithRadius(currentLatLng, 500); // 500m 반경으로 원 그리기
            return true;
        } else if (id == R.id.option3) {
            drawCircleWithRadius(currentLatLng, 1000); // 1km 반경으로 원 그리기
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    // 현재 위치를 기반으로 반경이 radius인 원을 그리는 메서드
    private void drawCircleWithRadius(LatLng latLng, int radius) {
        if (googleMap != null) {
            CircleOptions circleOptions = new CircleOptions()
                    .center(latLng)
                    .radius(radius) // 반경 설정 (미터 단위)
                    .strokeWidth(2)
                    .strokeColor(Color.RED)
                    .fillColor(Color.argb(70, 255, 0, 0)); // 채우기 색상 및 투명도 설정

            circle = googleMap.addCircle(circleOptions);

            // 반경 정보 출력
            Toast.makeText(this, "선택한 위치: " + latLng.toString() + ", 반경: " + radius + "m", Toast.LENGTH_SHORT).show();

            // 원을 일정 시간 후에 삭제하기 위한 Handler 객체 생성
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (circle != null) {
                        circle.remove(); // 원 제거
                        circle = null; // circle 객체 초기화
                    }
                }
            }, 8000); // 8000밀리초 후에 실행 (여기서는 8초 후)
        } else {
            Toast.makeText(this, "지도 객체가 null입니다. 원을 그릴 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        googleMap.setMyLocationEnabled(true);
                        startLocationUpdates();
                    }
                } else {
                    Toast.makeText(this, "위치 권한이 거부되었습니다.", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    // 캡처된 이미지를 저장하는 메서드
    private void captureMapScreen() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.map);

        if (fragment instanceof SupportMapFragment) {
            SupportMapFragment supportMapFragment = (SupportMapFragment) fragment;
            View mapView = supportMapFragment.getView();

            if (mapView != null) {
                // CompletableFuture를 사용하여 비트맵을 캡처하고 저장
                Screenshot.capture(mapView).thenAccept(bitmap -> {
                    if (bitmap != null) {
                        saveBitmap(bitmap);

                        // 캡처된 비트맵을 이미지 뷰에 설정
                        runOnUiThread(() -> {
                            ImageView imageView = findViewById(R.id.imageView);
                            imageView.setImageBitmap(bitmap);
                        });
                    } else {
                        // 사용자에게 오류 메시지를 표시
                        Toast.makeText(MainActivity.this, "이미지를 캡처하는 데 문제가 발생했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }


    // 외부 저장소 쓰기 권한을 확인하는 메서드
    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    // 외부 저장소 쓰기 권한을 요청하는 메서드
    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_STORAGE_PERMISSION);
    }

    // 비트맵을 캡처하고 저장하는 메서드
    private void captureAndSaveScreenshot(View view) {
        Screenshot.capture(view).whenComplete((bitmap, throwable) -> {
            if (bitmap != null) {
                saveBitmap(bitmap);
            } else {
                Toast.makeText(this, "캡처에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 비트맵을 저장하는 메서드
    private void saveBitmap(Bitmap bitmap) {
        // 저장할 파일의 디렉터리 설정
        // 외부 저장소의 DCIM/Camera 디렉토리를 설정
        File directory = new File(Environment.getExternalStorageDirectory(), "/DCIM/Camera");

        // 파일을 생성할 때 이 경로를 사용
        File file = new File(directory, "googlemap.png");


        // 파일 경로 확인 (디버깅용)
        Log.d("FilePath", file.getAbsolutePath());

        try {
            // 파일 출력 스트림 생성
            FileOutputStream outputStream = new FileOutputStream(file);
            // 비트맵을 PNG 형식으로 압축하여 출력 스트림에 저장
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            // 출력 스트림 닫기
            outputStream.flush();
            outputStream.close();

            // 사용자에게 이미지 저장 완료 메시지 표시
            Toast.makeText(this, "이미지가 저장되었습니다: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();

            // 갤러리 앱에 이미지 스캔 요청
            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
        } catch (IOException e) {
            e.printStackTrace();
            // 저장 실패 시 사용자에게 오류 메시지 표시
            Toast.makeText(this, "이미지 저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

}