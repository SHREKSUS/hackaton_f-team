package com.example.f_bank.api

import com.example.f_bank.api.model.CardResponse
import com.example.f_bank.api.model.CardsCheckResponse
import com.example.f_bank.api.model.CreateCardRequest
import com.example.f_bank.api.model.LinkCardByPhoneRequest
import com.example.f_bank.api.model.LinkPhoneToCardRequest
import com.example.f_bank.api.model.LoginRequest
import com.example.f_bank.api.model.LoginResponse
import com.example.f_bank.api.model.PinResponse
import com.example.f_bank.api.model.RegisterRequest
import com.example.f_bank.api.model.RegisterResponse
import com.example.f_bank.api.model.SavePinRequest
import com.example.f_bank.api.model.TransferRequest
import com.example.f_bank.api.model.TransferByPhoneRequest
import com.example.f_bank.api.model.TransferResponse
import com.example.f_bank.api.model.OtherBankTransferRequest
import com.example.f_bank.api.model.InternationalTransferRequest
import com.example.f_bank.api.model.UserByPhoneResponse
import com.example.f_bank.api.model.GetUserByPhoneRequest
import com.example.f_bank.api.model.VerifyPinRequest
import com.example.f_bank.api.model.DepositRequest
import com.example.f_bank.api.model.DepositResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    
    @POST("auth/save-pin")
    suspend fun savePin(@Body request: SavePinRequest): Response<PinResponse>
    
    @POST("auth/verify-pin")
    suspend fun verifyPin(@Body request: VerifyPinRequest): Response<PinResponse>
    
    @GET("cards")
    suspend fun getCards(@Header("Authorization") token: String): Response<com.example.f_bank.api.model.CardsResponse>
    
    @GET("cards/check")
    suspend fun checkCards(@Header("Authorization") token: String): Response<CardsCheckResponse>
    
    @POST("cards/create")
    suspend fun createCard(@Header("Authorization") token: String, @Body request: CreateCardRequest): Response<CardResponse>
    
    @POST("cards/transfer")
    suspend fun transferBetweenCards(@Header("Authorization") token: String, @Body request: TransferRequest): Response<TransferResponse>
    
    @POST("cards/transfer-by-phone")
    suspend fun transferByPhone(@Header("Authorization") token: String, @Body request: TransferByPhoneRequest): Response<TransferResponse>
    
    @POST("cards/link-by-phone")
    suspend fun linkCardByPhone(@Header("Authorization") token: String, @Body request: LinkCardByPhoneRequest): Response<CardResponse>
    
    @POST("cards/link-phone")
    suspend fun linkPhoneToCard(@Header("Authorization") token: String, @Body request: LinkPhoneToCardRequest): Response<CardResponse>
    
    @POST("users/by-phone")
    suspend fun getUserByPhone(@Header("Authorization") token: String, @Body request: GetUserByPhoneRequest): Response<UserByPhoneResponse>
    
    @POST("cards/transfer-to-other-bank")
    suspend fun transferToOtherBank(@Header("Authorization") token: String, @Body request: OtherBankTransferRequest): Response<TransferResponse>
    
    @POST("cards/international-transfer")
    suspend fun internationalTransfer(@Header("Authorization") token: String, @Body request: InternationalTransferRequest): Response<TransferResponse>
    
    @POST("cards/deposit")
    suspend fun depositToCard(@Header("Authorization") token: String, @Body request: DepositRequest): Response<DepositResponse>
}

