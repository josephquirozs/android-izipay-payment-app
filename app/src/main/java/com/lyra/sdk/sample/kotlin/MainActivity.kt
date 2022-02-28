package com.lyra.sdk.sample.kotlin

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.AuthFailureError
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.lyra.sdk.Lyra
import com.lyra.sdk.callback.LyraHandler
import com.lyra.sdk.callback.LyraResponse
import com.lyra.sdk.exception.LyraException
import org.json.JSONObject

/**
 * Main activity
 * <p>
 * This activity allows to user to perform a payment using the Lyra Mobile SDK
 * <p>
 * In order to perform a quick test payment:
 * <li>You should deploy your merchant server in order to create a payment session</li>.
 * <li>Set the merchant server endpoint in the SERVER_URL constant</li>
 * <li>Build and launch the application</li>
 * <li>Click in Pay button and complete the payment process</li>
 * <p></p>
 * Please note that, for readability purposes in this example, we do not use logs
 *
 * @author Lyra Network
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "IzipaySDK"

        // FIXME: change by the right merchant payment server url
        private const val SERVER_URL =
            "http://192.168.1.55:8080/payment-api" // without / at the end, example https://myserverurl.com

        // FIXME: change by your public key
        private const val PUBLIC_KEY =
            "85459066:testpublickey_3b5mh2wjrrSXIAnksQo5LTGbMWUWRlwZTexMjXiqQpYye"

        // Environment TEST or PRODUCTION, refer to documentation for more information
        // FIXME: change by your targeted environment
        private const val PAYMENT_MODE = "TEST"

        // TRUE to display a "register the card" switch in the payment form
        private const val ASK_REGISTER_PAY = false

        // FIXME: Change by the right REST API Server Name (available in merchant BO: Settings->Shop->REST API Keys)
        private const val API_SERVER_NAME =
            "https://api.micuentaweb.pe" // without / at the end, example https://myapiservername.com

        // Payment parameters
        // FIXME: change currency for your targeted environment
        private const val CURRENCY = "PEN"
        private const val AMOUNT = "5095"
        private const val ORDER_ID = "123"

        //Customer informations
        private const val CUSTOMER_EMAIL = "customeremail@domain.com"
        private const val CUSTOMER_REFERENCE = "customerReference"

        //Basic auth
        // FIXME: set your basic auth credentials
        private const val SERVER_AUTH_USER = "85459066"
        private const val SERVER_AUTH_TOKEN =
            "testpassword_H404qQgK3O38bpPNReIksKxuNdcgbpC06tGYQwUKLheBk"
        private const val CREDENTIALS: String = "$SERVER_AUTH_USER:$SERVER_AUTH_TOKEN"
    }

    // Initialize a new RequestQueue instance
    private lateinit var requestQueue: RequestQueue

    private fun getOptions(): HashMap<String, Any> {
        val options = HashMap<String, Any>()

        options[Lyra.OPTION_API_SERVER_NAME] = API_SERVER_NAME

        // android.permission.NFC must be added on AndroidManifest file
        // options[Lyra.OPTION_NFC_ENABLED] = true

        // cards-camera-recognizer dependency must be added on gradle file
        // options[Lyra.OPTION_CARD_SCANNING_ENABLED] = true

        return options
    }

    /**
     * onCreate method
     * Activity creation and SDK initialization
     *
     * @param savedInstanceState
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        try {
            // FIXME: Change by the right REST API Server Name (available in merchant BO: Settings->Shop->REST API Keys)
            Lyra.initialize(applicationContext, PUBLIC_KEY, getOptions())
        } catch (exception: Exception) {
            // handle possible exceptions when initializing SDK (Ex: invalid public key format)
            exception.printStackTrace();
        }
        requestQueue = Volley.newRequestQueue(applicationContext)
    }

    /**
     * onPayClick method
     * Invokes the payment
     *
     * @param view View of the Pay button
     */
    fun onPayClick(view: View) {
        getPaymentContext(getPaymentParams())
    }

    /**
     * Create a JSONObject with all the payment params.
     *
     * Check API REST integration documentation
     */
    private fun getPaymentParams(): JSONObject {
        val json = JSONObject()
            .put("currency", CURRENCY)
            .put("amount", AMOUNT)
            .put("orderId", ORDER_ID)
            .put(
                "customer",
                JSONObject("{\"email\":$CUSTOMER_EMAIL, \"reference\":$CUSTOMER_REFERENCE}")
            )
            .put("formTokenVersion", Lyra.getFormTokenVersion())
            .put("mode", PAYMENT_MODE)
        if (ASK_REGISTER_PAY) {
            json.put("formAction", "ASK_REGISTER_PAY")
        }
        return json
    }

    /**
     * Performs the create operation, calling the merchant server.
     * This call creates the session on server and retrieves the payment information necessary to continue the process
     *
     * @param paymentParams the operation parameters
     */
    private fun getPaymentContext(paymentParams: JSONObject) {
        val requestUrl = "${SERVER_URL}/createPayment"
        Log.i(TAG, "Request POST to $requestUrl")
        Log.i(TAG, "Request body $paymentParams")
        val jsonObjectRequest: JsonObjectRequest =
            object : JsonObjectRequest(
                Method.POST, requestUrl,
                paymentParams,
                Response.Listener { response ->
                    //In this sample, we extract the formToken from the serverResponse, call processServerResponse() which execute the process method of the SDK
                    Log.i(TAG, "Response body $response")
                    processFormToken(extractFormToken(response.toString()))
                },
                Response.ErrorListener { error ->
                    //Please manage your error behaviour here
                    Log.i(TAG, "Error Creating Payment")
                    error.printStackTrace()
                    Toast.makeText(
                        applicationContext,
                        "Error Creating Payment",
                        Toast.LENGTH_LONG
                    ).show()
                }
            ) {
                /**
                 * Passing some request headers
                 */
                @Throws(AuthFailureError::class)
                override fun getHeaders(): Map<String, String> {
                    return constructBasicAuthHeaders()
                }
            }

        requestQueue.add(jsonObjectRequest)
    }

    /**
     * Return a formToken if extraction is done correctly
     * Return an empty formToken if an error occur -> SDK will return an INVALID_FORMTOKEN exception
     */
    private fun extractFormToken(serverResponse: String): String {
        try {
            val answer = JSONObject(serverResponse).getJSONObject("answer")
            val formToken = answer.optString("formToken")
            if (formToken.isBlank()) {
                // TODO Please manage your error behaviour here
                // in this case, an error is present in the serverResponse, check the returned errorCode errorMessage
                Toast.makeText(
                    applicationContext,
                    "extractFormToken() -> formToken is empty" + "\n" +
                            "errorCode = " + answer.getString("errorCode")!! + "\n" +
                            "errorMessage = " + answer.optString("errorMessage") + "\n" +
                            "detailedErrorCode = " + answer.optString("detailedErrorCode") + "\n" +
                            "detailedErrorMessage = " + answer.optString("detailedErrorMessage"),
                    Toast.LENGTH_LONG
                ).show()
            }
            return formToken
        } catch (throwable: Throwable) {
            // TODO Please manage your error behaviour here
            // in this case, the serverResponse isn't as expected, please check the input serverResponse param
            Toast.makeText(
                applicationContext,
                "Cannot extract formToken from serverResponse",
                Toast.LENGTH_LONG
            ).show()
            return ""
        }
    }

    /**
     * Calls the Lyra Mobile SDK in order to handle the payment operation
     *
     * @param formToken the formToken extracted from the information of the payment session
     */
    private fun processFormToken(formToken: String) {
        // Open the payment form
        Lyra.process(supportFragmentManager, formToken, object : LyraHandler {
            override fun onSuccess(lyraResponse: LyraResponse) {
                verifyPayment(lyraResponse)
            }

            override fun onError(lyraException: LyraException, lyraResponse: LyraResponse?) {
                Toast.makeText(
                    applicationContext,
                    "Payment fail: ${lyraException.errorMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    /**
     * Call the server to verify that the response received from payment platform through the SDK is valid and has not been modified.
     *
     * @param payload information about the result of the operation
     */
    fun verifyPayment(payload: LyraResponse) {
        val requestUrl = "${SERVER_URL}/verifyResult"
        Log.i(TAG, "Request POST to $requestUrl")
        Log.i(TAG, "Request body $payload")
        val jsonObjectRequest: JsonObjectRequest =
            object : JsonObjectRequest(
                Method.POST, requestUrl,
                payload,
                Response.Listener { response ->
                    //Check the response integrity by verifying the hash on your server
                    Log.i(TAG, "Response body $response")
                    Toast.makeText(
                        applicationContext,
                        "Payment success",
                        Toast.LENGTH_LONG
                    ).show()
                },
                Response.ErrorListener { error ->
                    //Manage error here, please refer to the documentation for more information
                    Log.i(TAG, "Payment verification fail")
                    error.printStackTrace()
                    Toast.makeText(
                        applicationContext,
                        "Payment verification fail",
                        Toast.LENGTH_LONG
                    ).show()
                }
            ) {
                /**
                 * Passing some request headers
                 */
                @Throws(AuthFailureError::class)
                override fun getHeaders(): Map<String, String> {
                    return constructBasicAuthHeaders()
                }
            }
        requestQueue.add(jsonObjectRequest)
    }

    private fun constructBasicAuthHeaders(): HashMap<String, String> {
        val headers =
            HashMap<String, String>()
        headers["Content-Type"] = "application/json; charset=utf-8"
        headers["Authorization"] =
            "Basic " + Base64.encodeToString(CREDENTIALS.toByteArray(), Base64.NO_WRAP)
        return headers
    }
}
