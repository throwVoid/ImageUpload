package net.unix8.imageupload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import net.unix8.imageupload.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {
	private String filename;
	private String cutnameString;
	private ImageView iv = null;
	private Button btn = null;
	private Button submit;
	private int responsecode = 0;
	private ProgressDialog proDialog;
	private String timeString;
	private EditText username_EditText;
	private String usernameString = "admin";
	String upLoadServerUri = "http://unix8.net/up.php";
	//是拍照还是选择已存在图片
	private final int SELECT_PHOTO = 1;
	private final int CAMERA_PHOTO = 2;
	//toast更新
	private final int UPLOAD_FAILED_TOAST = 0;
	private final int UPLOAD_SUCCESS_TOAST = 1;
	private final int NETWORK_EXCEPTION_TOAST = 2;
	private final int SERVICE_UNREACH_TOAST = 3;
	private final int USE_WIFI_TOAST = 4;
	private final int SERVICE_CONNECTED_TOAST = 5;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		init();
	}
	public boolean onCreateOptionsMenu(Menu menu) {
	      menu.add(0, 0, 0, "About:地质灾害监测系统图片上传工具");
	      menu.add(0, 1, 1, "Autor:unix8.net");
	      menu.add(0, 2, 2, "退出");
	      return true;
	   }
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 2:
			System.exit(0);
			break;
		}
		return true;

	}

	@SuppressLint("HandlerLeak")
	Handler PostHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case UPLOAD_SUCCESS_TOAST:
				if (proDialog != null) {
					proDialog.dismiss();
				}
				Toast.makeText(MainActivity.this, "上传成功", Toast.LENGTH_SHORT).show();

				break;
			case UPLOAD_FAILED_TOAST:
				if (proDialog != null) {
					proDialog.dismiss();
				}
				Toast.makeText(MainActivity.this, "上传失败", Toast.LENGTH_SHORT).show();
				break;
			case NETWORK_EXCEPTION_TOAST:
				if (proDialog != null) {
					proDialog.dismiss();
				}
				Toast.makeText(MainActivity.this, "网络异常", Toast.LENGTH_SHORT).show();
				break;
			case SERVICE_UNREACH_TOAST:
				if (proDialog != null) {
					proDialog.dismiss();
				}
				Toast.makeText(MainActivity.this, "服务器不能连接", Toast.LENGTH_SHORT).show();
			case USE_WIFI_TOAST:
				Toast.makeText(MainActivity.this, "建议使用wifi减少流量", Toast.LENGTH_SHORT).show();
				break;
			case SERVICE_CONNECTED_TOAST:
				Toast.makeText(MainActivity.this, "连接服务器成功", Toast.LENGTH_SHORT).show();
				break;
			default:
				break;
			}
		}
	};

	private void init() {
		iv = (ImageView) findViewById(R.id.imageView1);
		btn = (Button) findViewById(R.id.button1);
		username_EditText = (EditText) findViewById(R.id.username_edittext);
		submit = (Button) findViewById(R.id.submit);
		//设置Listener
		btn.setOnClickListener(this);
		submit.setOnClickListener(this);

	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.button1:
			ShowPickDialog();
			break;
		case R.id.submit:
			proDialog = ProgressDialog.show(MainActivity.this, "上传中..","请稍后....", true, true);
			usernameString = username_EditText.getText().toString();
			if (usernameString == null) {
				new AlertDialog.Builder(MainActivity.this)
						.setMessage("输入的工号信息为空，重新填写")
						.setNegativeButton("确定",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										// TODO Auto-generated method stub

									}
								}).create().show();
			}

			Thread PostThread = new Thread(new PostImageThread(usernameString,
					filename, upLoadServerUri));
			PostThread.start();
			break;
		default:
			break;
		}
	}

	/**
	 * 选择对话框
	 */
	private void ShowPickDialog() {
		new AlertDialog.Builder(this)
				.setTitle("选择图片")
				.setNegativeButton("相册", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						Intent intent = new Intent(Intent.ACTION_PICK, null);
						intent.setDataAndType(
								MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
						startActivityForResult(intent, SELECT_PHOTO);

					}
				})
				.setPositiveButton("拍照", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						dialog.dismiss();
						Date date = new Date(System.currentTimeMillis());
						SimpleDateFormat dateFormat = new SimpleDateFormat(
								"'IMG'_yyyyMMddHHmmss");
						timeString = dateFormat.format(date);
						createSDCardDir();
						Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
						intent.putExtra(MediaStore.EXTRA_OUTPUT, 
								Uri.fromFile(new File(Environment.getExternalStorageDirectory()
										+ "/DCIM/Camera", timeString + ".jpg")));
						startActivityForResult(intent, CAMERA_PHOTO);
					}
				}).show();
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		// 如果是直接从相册获取
		case SELECT_PHOTO:
			if (resultCode == RESULT_OK) {
				Uri uri = data.getData();
				Cursor cursor = getContentResolver().query(uri, null, null, null, null);
				cursor.moveToFirst();
				ContentResolver cr = this.getContentResolver();
				try {
					Bitmap bitmap = BitmapFactory.decodeStream(cr.openInputStream(uri));
					ImageView imageView = (ImageView) findViewById(R.id.imageView1);
					// 将Bitmap设定到ImageView
					imageView.setImageBitmap(bitmap);
					savaBitmap(bitmap);
					} catch (FileNotFoundException e) {
					// Log.e(“Exception”, e.getMessage(),e);
				}
			}
			break;
		// 调用相机拍照
		case CAMERA_PHOTO:
			if (resultCode == RESULT_OK) {
				try {
					Bitmap bitmap = this.getLoacalBitmap(
							Environment.getExternalStorageDirectory().getPath()
							+ "/DCIM/Camera/" + timeString + ".jpg");
					ImageView imageView = (ImageView) findViewById(R.id.imageView1);
					imageView.setImageBitmap(bitmap);
					savaBitmap(bitmap);
					} catch (Exception e) {
					// TODO: handle exception
				}
			}
		default:
			break;

		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	public Bitmap getLoacalBitmap(String url) {
		if (url != null) {
			try {
				FileInputStream fis = new FileInputStream(url);
				return BitmapFactory.decodeStream(fis);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return null;
		}
	}

	public void createSDCardDir() {
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			File sdcardDir = Environment.getExternalStorageDirectory();
			String path = sdcardDir.getPath() + "/DCIM/Camera";
			File path1 = new File(path);
			if (!path1.exists()) {
				path1.mkdirs();
			}
		}
	}

	// 将bitmap保存到本地图片上！
	public void savaBitmap(Bitmap bitmap) {
		Date date = new Date(System.currentTimeMillis());
		SimpleDateFormat dateFormat = new SimpleDateFormat("'IMG'_yyyyMMddHHmmss");
		cutnameString = dateFormat.format(date);
		filename = Environment.getExternalStorageDirectory().getPath() + "/"
				+ cutnameString + ".jpg";
		File f = new File(filename);
		FileOutputStream fOut = null;
		try {
			f.createNewFile();
			fOut = new FileOutputStream(f);
		} catch (Exception e) {
			e.printStackTrace();
		}
		bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);// 把Bitmap对象解析成流
		try {
			fOut.flush();
			fOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// 上传图片方法
	class PostImageThread implements Runnable {
		private String username;
		private String picpath;
		private String url;

		public PostImageThread(String username, String picpath, String url) {
			this.url = url;
			this.username = username;
			this.picpath = picpath;
		}

		@Override
		public void run() {
			int resultformServer = submit_Data(username, picpath, url);
			Message msg = PostHandler.obtainMessage();
			msg.what = resultformServer;
			PostHandler.sendMessage(msg);
		}

		public String JSONTokener(String in) {
			if (in != null && in.startsWith("\ufeff")) {
				in = in.substring(1);
			}
			return in;
		}

		public boolean note_Intent(Context context) {
			ConnectivityManager con = 
					(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo networkinfo = con.getActiveNetworkInfo();
			if (networkinfo == null || !networkinfo.isAvailable()) {
				Message msg = PostHandler.obtainMessage();
				msg.what = NETWORK_EXCEPTION_TOAST;
				PostHandler.sendMessage(msg);
				return false;
			}
			boolean wifi = 
					con.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();
			if (!wifi) {
				Message msg = PostHandler.obtainMessage();
				msg.what = USE_WIFI_TOAST;
				PostHandler.sendMessage(msg);
			}
			return true;

		}

		public int submit_Data(String username, String picpath, String url) {
			try {
				if (!note_Intent(MainActivity.this)) {
					Message msg = PostHandler.obtainMessage();
					msg.what = SERVICE_UNREACH_TOAST;
					PostHandler.sendMessage(msg);
					responsecode = UPLOAD_FAILED_TOAST;
					proDialog.dismiss();
					return responsecode;
				}
			} catch (Exception e) {
				Message msg = PostHandler.obtainMessage();
				msg.what = SERVICE_UNREACH_TOAST;
				PostHandler.sendMessage(msg);
				//e.printStackTrace();
				responsecode = UPLOAD_FAILED_TOAST;
				proDialog.dismiss();
				return responsecode;
			}
			Message msg = PostHandler.obtainMessage();
			msg.what = 5;
			PostHandler.sendMessage(msg);
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("image", picpath));
			params.add(new BasicNameValuePair("username", username));

			HttpClient httpClient = new DefaultHttpClient();
			HttpContext localContext = new BasicHttpContext();
			HttpPost httpPost = new HttpPost(url);
			try {
				MultipartEntity entity = new MultipartEntity(
						HttpMultipartMode.BROWSER_COMPATIBLE);
				for (int index = 0; index < params.size(); index++) {
					if (params.get(index).getName().equalsIgnoreCase("image")) {

						entity.addPart(params.get(index).getName(),
								new FileBody(new File(params.get(index).getValue())));

					} else {
						entity.addPart(params.get(index).getName(),
								new StringBody(params.get(index).getValue(),
										Charset.forName("UTF-8")));
					}
				}
				httpPost.setEntity(entity);
				HttpResponse response = httpClient.execute(httpPost, localContext);
				responsecode = response.getStatusLine().getStatusCode();
				if (responsecode == 200) {					
					HttpEntity resEntity = response.getEntity();
					String Response = EntityUtils.toString(resEntity);
					Log.d("Response:", Response);
					if (Response.equals("Success")) {
						responsecode = UPLOAD_SUCCESS_TOAST;
					} else
						responsecode = UPLOAD_FAILED_TOAST;
				}
			} catch (Exception e) {
				e.printStackTrace();
				proDialog.dismiss();
				return responsecode;
			}
			return responsecode;

		}
	}
}
