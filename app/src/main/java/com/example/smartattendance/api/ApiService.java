package com.example.smartattendance.api;

import com.example.smartattendance.models.LoginResponse;
import com.google.gson.JsonObject;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {

    @GET("connect.php")
    Call<JsonObject> checkConnection();

    // API Đăng nhập
    @FormUrlEncoded
    @POST("login.php")
    Call<LoginResponse> login(
            @Field("username") String username,
            @Field("password") String password
    );
    @Multipart
    @POST("upload_face.php")
    Call<JsonObject> uploadFace(
            @Part MultipartBody.Part image,
            @Part("username") RequestBody username
    );
}