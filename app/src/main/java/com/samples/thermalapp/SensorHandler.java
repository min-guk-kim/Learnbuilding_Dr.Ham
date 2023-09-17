package com.samples.thermalapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import org.json.*;




public class SensorHandler {
    public static String Authenticate() throws IOException {

        //sets up for a post
        URL url = new URL(passwords.BASEURL + "/oauth/authorize");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);

        //adds params
        String jsonInputString = "{\"email\": \"" + passwords.EMAIL+ "\", \"password\": \""+passwords.PASSWORD+"\"}";
        //System.out.println(jsonInputString);
        try(OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        //reads response
        String authorizationToken = "";
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            //unpacks json
            JSONObject obj = new JSONObject(response.toString());
            authorizationToken = obj.getString("authorization");
            //System.out.println(authorizationToken);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        //sets up for another post
        URL url1 = new URL(passwords.BASEURL + "/oauth/accesstoken");
        HttpURLConnection con1 = (HttpURLConnection) url1.openConnection();
        con1.setRequestMethod("POST");
        con1.setRequestProperty("Content-Type", "application/json; utf-8");
        con1.setRequestProperty("Accept", "application/json");
        con1.setDoOutput(true);

        //adds params
        String jsonInputString1 = "{\"authorization\": \"" + authorizationToken+ "\", \"password\": \""+passwords.PASSWORD+"\"}";
        //System.out.println(jsonInputString1);
        try(OutputStream os = con1.getOutputStream()) {
            byte[] input = jsonInputString1.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        //reads response
        String oauthtoken = "";
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(con1.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            //unpacks json
            JSONObject obj = new JSONObject(response.toString());
            oauthtoken = obj.getString("accesstoken");
            System.out.println("oauth "  +oauthtoken);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    System.out.println("authenticated");
    return oauthtoken;
    }


    public static ArrayList<Double> QuerySamples(String oauthKey,int sensorID) throws IOException
    {
        //sets up for a post
        URL url = new URL(passwords.BASEURL + "/samples");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Authorization",oauthKey);
        con.setDoOutput(true);

        //adds params
        String jsonInputString = "{\"limit\": " + 1 + "}";
        //System.out.println(jsonInputString);
        try(OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        //reads response
        ArrayList<Double> readings = new ArrayList<Double>();
        try(BufferedReader br = new BufferedReader(
            new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                //unpacks json
                JSONObject sensor1JsonObject = new JSONObject(response.toString());
                sensor1JsonObject = sensor1JsonObject.getJSONObject("sensors");
                //System.out.println(obj);
                JSONObject sensor2JsonObject = sensor1JsonObject.getJSONArray(passwords.SENSORNAME2).getJSONObject(0);
                sensor1JsonObject = sensor1JsonObject.getJSONArray(passwords.SENSORNAME1).getJSONObject(0);

                System.out.println("reading sensor1 "+ sensor1JsonObject);
                System.out.println("reading sensor2 "+ sensor2JsonObject);

                if(sensorID == 1)
                {
                    readings.add(Double.parseDouble(sensor1JsonObject.getString("temperature")));
                    readings.add(Double.parseDouble(sensor1JsonObject.getString("humidity")));
                }
                if(sensorID == 2)
                {
                    readings.add(Double.parseDouble(sensor2JsonObject.getString("temperature")));
                    readings.add(Double.parseDouble(sensor2JsonObject.getString("humidity")));
                }

                System.out.println(readings);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return readings;
    }


}
