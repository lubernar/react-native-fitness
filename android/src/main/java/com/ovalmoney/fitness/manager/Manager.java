package com.ovalmoney.fitness.manager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.fitness.result.SessionReadResponse;
import com.google.android.gms.fitness.request.SessionInsertRequest;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.Task;
import com.ovalmoney.fitness.permission.Request;

import java.text.DateFormat;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.UUID;

import static com.ovalmoney.fitness.permission.Permissions.ACTIVITY;
import static com.ovalmoney.fitness.permission.Permissions.CALORIES;
import static com.ovalmoney.fitness.permission.Permissions.DISTANCES;
import static com.ovalmoney.fitness.permission.Permissions.STEPS;
import static com.ovalmoney.fitness.permission.Permissions.HEART_RATE;
import static com.ovalmoney.fitness.permission.Permissions.SLEEP_ANALYSIS;
import static com.ovalmoney.fitness.permission.Permissions.WEIGHT;
import static com.ovalmoney.fitness.permission.Permissions.HEIGHT;

public class Manager implements ActivityEventListener {

    private final static int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 111;
    private final static int GOOGLE_PLAY_SERVICE_ERROR_DIALOG = 2404;
    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault());

    private Promise promise;

    private static boolean isGooglePlayServicesAvailable(final Activity activity) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int status = googleApiAvailability.isGooglePlayServicesAvailable(activity);
        if(status != ConnectionResult.SUCCESS) {
            if(googleApiAvailability.isUserResolvableError(status)) {
                googleApiAvailability.getErrorDialog(activity, status, GOOGLE_PLAY_SERVICE_ERROR_DIALOG).show();
            }
            return false;
        }
        return true;
    }

    private static TimeUnit getInterval(String customInterval) {
        if(customInterval.equals("minute")) {
            return TimeUnit.MINUTES;
        }
        if(customInterval.equals("hour")) {
            return TimeUnit.HOURS;
        }
        return TimeUnit.DAYS;
    }

    protected FitnessOptions.Builder addPermissionToFitnessOptions(final FitnessOptions.Builder fitnessOptions, final ArrayList<Request> permissions){
        int length = permissions.size();
        for(int i = 0; i < length; i++){
            Request currentRequest = permissions.get(i);
            switch(currentRequest.permissionKind){
                case STEPS:
                    fitnessOptions
                            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, currentRequest.permissionAccess)
                            .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE, currentRequest.permissionAccess);
                    break;
                case DISTANCES:
                    fitnessOptions.addDataType(DataType.TYPE_DISTANCE_DELTA, currentRequest.permissionAccess);
                    break;
                case CALORIES:
                    fitnessOptions.addDataType(DataType.TYPE_CALORIES_EXPENDED, currentRequest.permissionAccess);
                    break;
                case ACTIVITY:
                    fitnessOptions.addDataType(DataType.TYPE_ACTIVITY_SEGMENT, currentRequest.permissionAccess);
                    break;
                case HEART_RATE:
                    fitnessOptions.addDataType(DataType.TYPE_HEART_RATE_BPM, currentRequest.permissionAccess);
                    break;
                case WEIGHT:
                    fitnessOptions.addDataType(DataType.TYPE_WEIGHT, currentRequest.permissionAccess);
                    break;
                case HEIGHT:
                    fitnessOptions.addDataType(DataType.TYPE_HEIGHT, currentRequest.permissionAccess);
                    break;
                case SLEEP_ANALYSIS:
                default:
                    break;
            }
        }

        return fitnessOptions;
    }

    public boolean isAuthorized(final Activity activity, final ArrayList<Request> permissions){
        if(isGooglePlayServicesAvailable(activity)) {
            final FitnessOptions fitnessOptions = addPermissionToFitnessOptions(FitnessOptions.builder(), permissions)
                    .build();
            return GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(activity), fitnessOptions);
        }
        return false;
    }

    public void requestPermissions(@NonNull Activity currentActivity, final ArrayList<Request> permissions, Promise promise) {
        try {
            this.promise = promise;
            FitnessOptions fitnessOptions = addPermissionToFitnessOptions(FitnessOptions.builder(), permissions)
                    .build();
            GoogleSignIn.requestPermissions(
                    currentActivity,
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(currentActivity.getApplicationContext()),
                    fitnessOptions);
        }catch(Exception e){
            Log.e(getClass().getName(), e.getMessage());
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            promise.resolve(true);
        }
        if (resultCode == Activity.RESULT_CANCELED && requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            promise.resolve(false);
        }
    }


    public void logout(@NonNull Activity currentActivity, final Promise promise) {
        final GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        GoogleSignIn.getClient(currentActivity, gso)
            .revokeAccess()
            .addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
                promise.resolve(false);
            }
            })
            .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                promise.resolve(true);
            }
            })
            .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                promise.reject(e);
            }
            })
            .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                promise.reject(e);
            }
        });
        }

    public void disconnect(@NonNull Activity currentActivity, final Promise promise) {
        Fitness.getConfigClient(
            currentActivity,
            GoogleSignIn.getLastSignedInAccount(currentActivity.getApplicationContext()
            ))
          .disableFit()
          .addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
              promise.resolve(false);
            }
          })
          .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
              promise.resolve(true);
            }
          })
          .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
              promise.reject(e);
            }
          });
    }

    @Override
    public void onNewIntent(Intent intent) { }

    public void subscribeToSteps(Context context, final Promise promise){
        final GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if(account == null){
            promise.resolve(false);
            return;
        }
        Fitness.getRecordingClient(context, account)
                .subscribe(DataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        promise.resolve(true);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.resolve(false);
                    }
                });
    }

    public void getSteps(Context context, double startDate, double endDate, String customInterval, final Promise promise){
        DataSource ESTIMATED_STEP_DELTAS = new DataSource.Builder()
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setType(DataSource.TYPE_DERIVED)
                .setStreamName("estimated_steps")
                .setAppPackageName("com.google.android.gms")
                .build();

        TimeUnit interval = getInterval(customInterval);

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(ESTIMATED_STEP_DELTAS,    DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, interval)
                .setTimeRange((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        if (dataReadResponse.getBuckets().size() > 0) {
                            WritableArray steps = Arguments.createArray();
                            for (Bucket bucket : dataReadResponse.getBuckets()) {
                                List<DataSet> dataSets = bucket.getDataSets();
                                for (DataSet dataSet : dataSets) {
                                    processStep(dataSet, steps);
                                }
                            }
                            promise.resolve(steps);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                    }
                });
    }

    public void getDistances(Context context, double startDate, double endDate, String customInterval, final Promise promise) {
        TimeUnit interval = getInterval(customInterval);

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .bucketByTime(1, interval)
                .setTimeRange((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        if (dataReadResponse.getBuckets().size() > 0) {
                            WritableArray distances = Arguments.createArray();
                            for (Bucket bucket : dataReadResponse.getBuckets()) {
                                List<DataSet> dataSets = bucket.getDataSets();
                                for (DataSet dataSet : dataSets) {
                                    processDistance(dataSet, distances);
                                }
                            }
                            promise.resolve(distances);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                    }
                });
    }

    public void getCalories(Context context, double startDate, double endDate, String customInterval, final Promise promise) {
        TimeUnit interval = getInterval(customInterval);

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .bucketByTime(1, interval)
                .setTimeRange((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        if (dataReadResponse.getBuckets().size() > 0) {
                            WritableArray calories = Arguments.createArray();
                            for (Bucket bucket : dataReadResponse.getBuckets()) {
                                List<DataSet> dataSets = bucket.getDataSets();
                                for (DataSet dataSet : dataSets) {
                                    processCalories(dataSet, calories);
                                }
                            }
                            promise.resolve(calories);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                    }
                });
    }
    


    public void getWeightAndHeight(Context context, Promise promise) {
        FitnessOptions fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_WEIGHT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_HEIGHT, FitnessOptions.ACCESS_READ)
        .build();

        accessGoogleFit(context, promise);
	}

    public void submitWorkout(Context context, String name, String workoutType, double startTime, double endTime, float calories, float distance, final Promise promise) {

        /*
        * Distance Data source
        */ 
        DataSource distanceDataSource = new DataSource.Builder()
                .setAppPackageName(context.getPackageName())
                .setDataType(DataType.TYPE_DISTANCE_DELTA)
                .setType(DataSource.TYPE_RAW).build();
        DataSet distanceDataSet = DataSet.create(distanceDataSource);
        DataPoint distanceDataPoint = DataPoint.create(distanceDataSource)
                .setTimeInterval((long) startTime, (long) endTime, TimeUnit.MILLISECONDS);
        distanceDataPoint.getValue(Field.FIELD_DISTANCE).setFloat(distance);
        distanceDataSet.add(distanceDataPoint);

        /*
        * Calories Data source
        */ 
        DataSource caloriesDataSource = new DataSource.Builder()
                .setAppPackageName(context.getPackageName())
                .setDataType(DataType.TYPE_CALORIES_EXPENDED)
                .setType(DataSource.TYPE_RAW).build();
        DataSet caloriesDataSet = DataSet.create(caloriesDataSource);
        DataPoint caloriesDataPoint = DataPoint.create(caloriesDataSource)
                .setTimeInterval((long) startTime, (long) endTime, TimeUnit.MILLISECONDS);
        caloriesDataPoint.getValue(Field.FIELD_CALORIES).setFloat(calories);
        caloriesDataSet.add(caloriesDataPoint);

        /*
        * Activity data source
        */
        DataSource acSource = new DataSource.Builder()
                .setAppPackageName(context.getPackageName())
                .setDataType(DataType.TYPE_ACTIVITY_SEGMENT)
                .setType(DataSource.TYPE_DERIVED).build();
        DataSet dataSet = DataSet.create(acSource);
        DataPoint dataPoint = DataPoint.create(acSource).setTimeInterval((long) startTime, (long) endTime, TimeUnit.MILLISECONDS);
        dataPoint.getValue(Field.FIELD_ACTIVITY).setActivity(getActivityType(workoutType));
        dataSet.add(dataPoint);

        // Persist everything in google store
        Session session = new Session.Builder()
                .setName(name)
                // .setActivity(getActivityType(workoutType))
                .setActivity(FitnessActivities.CRICKET)
                .setDescription("activity description")
                .setIdentifier(UUID.randomUUID().toString())
                .setStartTime((long) startTime, TimeUnit.MILLISECONDS)
                .setEndTime((long) endTime, TimeUnit.MILLISECONDS)
                .build();

        SessionInsertRequest insertRequest = new SessionInsertRequest.Builder()
                .setSession(session)
                .addDataSet(caloriesDataSet)
                .addDataSet(dataSet)
                .addDataSet(distanceDataSet)
                .build();

            Fitness.getSessionsClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .insertSession(insertRequest)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    // At this point, the session has been inserted and can be read.
                    Log.d("LOG_TAG", "onSuccess()");
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    promise.reject(e);
                }
            });

        }

     public void getHeartRate(Context context, double startDate, double endDate, String customInterval,final Promise promise) {
        TimeUnit interval = getInterval(customInterval);

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_HEART_RATE_BPM, DataType.AGGREGATE_HEART_RATE_SUMMARY)
                .bucketByTime(1, interval)
                .setTimeRange((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        if (dataReadResponse.getBuckets().size() > 0) {
                            WritableArray heartRates = Arguments.createArray();
                            for (Bucket bucket : dataReadResponse.getBuckets()) {
                                List<DataSet> dataSets = bucket.getDataSets();
                                for (DataSet dataSet : dataSets) {
                                    processHeartRate(dataSet, heartRates);
                                }
                            }
                            promise.resolve(heartRates);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                    }
                });
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void getSleepAnalysis(Context context, double startDate, double endDate, final Promise promise) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N){
            promise.reject(String.valueOf(FitnessError.ERROR_METHOD_NOT_AVAILABLE), "Method not available");
            return;
        }

        SessionReadRequest request = new SessionReadRequest.Builder()
                .readSessionsFromAllApps()
                .read(DataType.TYPE_ACTIVITY_SEGMENT)
                .setTimeInterval((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getSessionsClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .readSession(request)
                .addOnSuccessListener(new OnSuccessListener<SessionReadResponse>() {
                    @Override
                    public void onSuccess(SessionReadResponse response) {
                        List<Object> sleepSessions = response.getSessions()
                            .stream()
                            .filter(new Predicate<Session>() {
                                @Override
                                public boolean test(Session s) {
                                    return s.getActivity().equals(FitnessActivities.SLEEP);
                                }
                            })
                            .collect(Collectors.toList());

                        WritableArray sleep = Arguments.createArray();
                        for (Object session : sleepSessions) {
                            List<DataSet> dataSets = response.getDataSet((Session) session);
                            for (DataSet dataSet : dataSets) {
                                processSleep(dataSet, (Session) session, sleep);
                            }
                        }

                        promise.resolve(sleep);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                });
    }

    private String getActivityType(String workoutType) {
        String activityType;

        switch (workoutType) {
            case "running":
                activityType = FitnessActivities.RUNNING;
                break;
            case "rowing":
                activityType = FitnessActivities.ROWING;
                break;
            case "cycling":
                activityType = FitnessActivities.BIKING;
                break;
            default:
                activityType = FitnessActivities.OTHER;
                break;
        }

        return activityType;
    }

    private void accessGoogleFit(Context context, final Promise promise) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		long endTime = cal.getTimeInMillis();

		DataReadRequest readRequest = new DataReadRequest.Builder()
        .read(DataType.TYPE_WEIGHT)
        .read(DataType.TYPE_HEIGHT)
        .setLimit(1)
        .setTimeRange(1, (new Date().getTime()), TimeUnit.MILLISECONDS)
        .build();

		final GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);

		Fitness.getHistoryClient(context, account)
				.readData(readRequest)
				.addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
					@Override
					public void onSuccess(DataReadResponse dataReadResponse) {
						Log.d("LOG_TAG", "onSuccess()");

						List<DataSet> dataSets = dataReadResponse.getDataSets();

						WritableMap map = Arguments.createMap();

						try {
							for (DataSet ds : dataSets) {
								for (DataPoint dp : ds.getDataPoints()) {
									for (Field field : dp.getDataType().getFields()) {
										map.putDouble(field.getName(), dp.getValue(field).asFloat());
									}
								}
							}	
						}
						catch (Exception e) {
							promise.reject(e.toString());
						}
						
						promise.resolve(map);
					}
				})
				.addOnFailureListener(new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						Log.e("LOG_TAG", "onFailure()", e);
						promise.reject(e.toString());
					}
				})
				.addOnCompleteListener(new OnCompleteListener() {
					@Override
					public void onComplete(@NonNull Task task) {
						Log.d("LOG_TAG", "onComplete()");
					}
				});
	}

    private void processStep(DataSet dataSet, WritableArray map) {

        for (DataPoint dp : dataSet.getDataPoints()) {
            for(Field field : dp.getDataType().getFields()) {
                WritableMap stepMap = Arguments.createMap();
                stepMap.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                stepMap.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                stepMap.putDouble("quantity", dp.getValue(field).asInt());
                map.pushMap(stepMap);
            }
        }
    }

    private void processDistance(DataSet dataSet, WritableArray map) {

        for (DataPoint dp : dataSet.getDataPoints()) {
            for(Field field : dp.getDataType().getFields()) {
                WritableMap distanceMap = Arguments.createMap();
                distanceMap.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                distanceMap.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                distanceMap.putDouble("quantity", dp.getValue(field).asFloat());
                map.pushMap(distanceMap);
            }
        }
    }

    private void processCalories(DataSet dataSet, WritableArray map) {

        for (DataPoint dp : dataSet.getDataPoints()) {
            for(Field field : dp.getDataType().getFields()) {
                WritableMap caloryMap = Arguments.createMap();
                caloryMap.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                caloryMap.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                caloryMap.putDouble("quantity", dp.getValue(field).asFloat());
                map.pushMap(caloryMap);
            }
        }
    }

    private void processHeartRate(DataSet dataSet, WritableArray map) {

        for (DataPoint dp : dataSet.getDataPoints()) {
            for(Field field : dp.getDataType().getFields()) {
                WritableMap heartRateMap = Arguments.createMap();
                heartRateMap.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                heartRateMap.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                heartRateMap.putDouble("quantity", dp.getValue(field).asFloat());
                map.pushMap(heartRateMap);
            }
        }
    }

    private void processSleep(DataSet dataSet, Session session, WritableArray map) {

        for (DataPoint dp : dataSet.getDataPoints()) {
            for(Field field : dp.getDataType().getFields()) {
                WritableMap sleepMap = Arguments.createMap();
                sleepMap.putString("value", dp.getValue(field).asActivity());
                sleepMap.putString("sourceId", session.getIdentifier());
                sleepMap.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                sleepMap.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                map.pushMap(sleepMap);
            }
        }
    }
}
