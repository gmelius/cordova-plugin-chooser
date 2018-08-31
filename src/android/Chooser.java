package com.cyph.cordova;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.file.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.pwlin.cordova.plugins.fileopener2.FileProvider;


public class Chooser extends CordovaPlugin{
	private static final String ACTION_OPEN = "getFile";
	private static final int PICK_FILE_REQUEST = 1;
	private static final String TAG = "Chooser";
	private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;

	/** @see https://stackoverflow.com/a/17861016/459881 */
	public static byte[] getBytesFromInputStream (InputStream is) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] buffer = new byte[0xFFFF];

		for (int len = is.read(buffer); len != -1; len = is.read(buffer)) {
			os.write(buffer, 0, len);
		}

		return os.toByteArray();
	}

	/** @see https://stackoverflow.com/a/23270545/459881 */
	public static String getDisplayName (ContentResolver contentResolver, Uri uri) {
		String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
		Cursor metaCursor = contentResolver.query(uri, projection, null, null, null);

		if (metaCursor != null) {
			try {
				if (metaCursor.moveToFirst()) {
					return metaCursor.getString(0);
				}
			} finally {
				metaCursor.close();
			}
		}

		return "File";
	}


	@SuppressLint("NewApi")
	public static String getPath(Context context, Uri uri) {
		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
		final boolean isM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
		if (!isM || context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
			// DocumentProvider
			if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
				// ExternalStorageProvider
				if (isExternalStorageDocument(uri)) {
					final String docId = DocumentsContract.getDocumentId(uri);
					final String[] split = docId.split(":");
					final String type = split[0];

					if ("primary".equalsIgnoreCase(type)) {
						return Environment.getExternalStorageDirectory() + "/" + split[1];
					}
					// TODO handle non-primary volumes
				}
				// DownloadsProvider
				else if (isDownloadsDocument(uri)) {
					final String id = DocumentsContract.getDocumentId(uri);
					final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
					return getDataColumn(context, contentUri, null, null);
				}
				// MediaProvider
				else
				if (isMediaDocument(uri)) {
					final String docId = DocumentsContract.getDocumentId(uri);
					final String[] split = docId.split(":");
					final String type = split[0];
					Uri contentUri = null;
					if ("image".equals(type)) {
						contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
					} else if ("video".equals(type)) {
						contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
					} else if ("audio".equals(type)) {
						contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
					}
					final String selection = "_id=?";
					final String[] selectionArgs = new String[] {split[1]};
					return getDataColumn(context, contentUri, selection, selectionArgs);
				}
			}
			// MediaStore (and general)
			else if ("content".equalsIgnoreCase(uri.getScheme())) {
				// Return the remote address
				if (isGooglePhotosUri(uri))
					return uri.getLastPathSegment();
				return getDataColumn(context, uri, null, null);
			}
			// File
			else if ("file".equalsIgnoreCase(uri.getScheme())) {
				return uri.getPath();
			}
		}
		return null;
	}

	public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = { column };
		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
			if (cursor != null && cursor.moveToFirst()) {
				final int index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}

	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}





	private CallbackContext callback;
	private String accept;

	@SuppressLint("NewApi")
	public void chooseFile () throws JSONException {
		Context context = cordova.getActivity().getApplicationContext();

		final boolean isM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
		if (!isM || context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
			try {
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType(this.accept);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
				intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

				Intent chooser = Intent.createChooser(intent, "Select File");
				cordova.startActivityForResult(this, chooser, Chooser.PICK_FILE_REQUEST);

				PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
				pluginResult.setKeepCallback(true);

				this.callback.sendPluginResult(pluginResult);
			} catch (android.content.ActivityNotFoundException e) {
				JSONObject errorObj = new JSONObject();
				errorObj.put("status", PluginResult.Status.ERROR.ordinal());
				errorObj.put("message", "Activity not found: " + e.getMessage());
				this.callback.error(errorObj);
			}
		} else {
			// Send request permission.
			cordova.requestPermissions(this,
					PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE,
					new String[] {Manifest.permission.READ_EXTERNAL_STORAGE});
		}
	}

	@Override
	public boolean execute (
		String action,
		JSONArray args,
		CallbackContext callbackContext
	) {
		try {
			if (action.equals(Chooser.ACTION_OPEN)) {
				this.callback = callbackContext;
				this.accept = args.getString(0);
				this.chooseFile();
				return true;
			}
		}
		catch (JSONException err) {
			this.callback.error("Execute failed: " + err.toString());
		}

		return false;
	}

	@Override
	public void onActivityResult (int requestCode, int resultCode, Intent data) {
		try {
			if (requestCode == Chooser.PICK_FILE_REQUEST && this.callback != null) {
				if (resultCode == Activity.RESULT_OK) {
					Uri uri = data.getData();

					if (uri != null) {
						ContentResolver contentResolver =
							this.cordova.getActivity().getContentResolver()
						;

						String name = Chooser.getDisplayName(contentResolver, uri);

						String mediaType = contentResolver.getType(uri);
						if (mediaType == null || mediaType.isEmpty()) {
							mediaType = "application/octet-stream";
						}

						byte[] bytes = Chooser.getBytesFromInputStream(
							contentResolver.openInputStream(uri)
						);

						String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);

						JSONObject result = new JSONObject();

						result.put("data", base64);
						result.put("mediaType", mediaType);
						result.put("name", name);
						result.put("uri", uri.toString());
						result.put("url", getPath(this.cordova.getActivity(), uri));

						this.callback.success(result.toString());
					}
					else {
						this.callback.error("File URI was null.");
					}
				}
				else if (resultCode == Activity.RESULT_CANCELED) {
					this.callback.success("RESULT_CANCELED");
				}
				else {
					this.callback.error(resultCode);
				}
			}
		}
		catch (IOException|JSONException err) {
			this.callback.error("Failed to read file: " + err.toString());
		}
	}

	@Override
	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
		super.onRequestPermissionResult(requestCode, permissions, grantResults);
		if (requestCode == PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				// Permission was granted
				this.chooseFile();
			} else {
				JSONObject errorObj = new JSONObject();
				errorObj.put("status", PluginResult.Status.ERROR.ordinal());
				errorObj.put("message", "Permission denied");
				this.callback.error(errorObj);
			}
		}
	}
}