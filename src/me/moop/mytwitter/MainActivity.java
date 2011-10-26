package me.moop.mytwitter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	Button mBtnDownload;
	AutoCompleteTextView mActxtUsername;
	ProgressDialog mProgressDialog;

	TextView mTxtvUserNameTitle;
	TextView mTxtvUserName;
	TextView mTxtvUrlTitle;
	TextView mTxtvUrl;
	TextView mTxtvFavouritesCountTitle;
	TextView mTxtvFavouritesCount;
	TextView mTxtvDescriptionTitle;
	TextView mTxtvDescription;
	Button mBtnTweets;

	TwitterUser mTwitterUser;
	DatabaseHelper mDatabaseHelper;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nicelayout);

		mBtnDownload = (Button) findViewById(R.id.btnDownload);
		mActxtUsername = (AutoCompleteTextView) findViewById(R.id.actxtvUsername);

		mTxtvUserNameTitle = (TextView) findViewById(R.id.txtvUserNameTitle);
		mTxtvUserName = (TextView) findViewById(R.id.txtvUserName); 
		mTxtvUrlTitle = (TextView) findViewById(R.id.txtvUrlTitle); 
		mTxtvUrl = (TextView) findViewById(R.id.txtvUrl);

		mTxtvFavouritesCountTitle = (TextView) findViewById(R.id.txtvFavouritesCountTitle);
		mTxtvFavouritesCount = (TextView) findViewById(R.id.txtvFavouritesCount); 
		mTxtvDescriptionTitle = (TextView) findViewById(R.id.txtvDescriptionTitle); 
		mTxtvDescription = (TextView) findViewById(R.id.txtvDescription);

		mBtnTweets = (Button) findViewById(R.id.btnTweets);

		updateView();
	}

	public void downloadUserInfo(View view){
		if (view == mBtnDownload){
			String username = mActxtUsername.getText().toString();
			if (username.length() > 0){
				downloadOrShowFromDb(username);
			}
			else{
				Toast.makeText(this, "Voer een twitter gebruikersnaam in", Toast.LENGTH_LONG).show();
			}
		}
	}

	public void showTweets(View view){
		if (view == mBtnTweets){
			Intent intent = new Intent(this, TweetsActivity.class);
			intent.putExtra("twitter_user_name", mTwitterUser.getUserName());
			startActivity(intent);
		}
	}

	private DefaultHttpClient createHttpClient() {
		HttpParams my_httpParams = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(my_httpParams, 3000);
		SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		ThreadSafeClientConnManager multiThreadedConnectionManager = new ThreadSafeClientConnManager(my_httpParams, registry);
		DefaultHttpClient httpclient = new DefaultHttpClient(multiThreadedConnectionManager, my_httpParams);
		return httpclient;
	}

	private void updateView(){
		
		Dao<TwitterUser, String> twitterUsersDao;
		try {
			twitterUsersDao = getDatabaseHelper().getTwitterUsersDao();
			List<TwitterUser> twitterUsers = twitterUsersDao.queryForAll();
			ArrayAdapter<TwitterUser> adapter = new ArrayAdapter<TwitterUser>(this, android.R.layout.simple_list_item_1, twitterUsers);
			mActxtUsername.setAdapter(adapter);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		if (mTwitterUser == null){
			mTxtvUrlTitle.setVisibility(View.INVISIBLE);
			mTxtvUrl.setVisibility(View.INVISIBLE);
			mTxtvUserNameTitle.setVisibility(View.INVISIBLE);
			mTxtvUserName.setVisibility(View.INVISIBLE);

			mTxtvFavouritesCountTitle.setVisibility(View.INVISIBLE);
			mTxtvFavouritesCount.setVisibility(View.INVISIBLE);
			mTxtvDescriptionTitle.setVisibility(View.INVISIBLE);
			mTxtvDescription.setVisibility(View.INVISIBLE);

			mBtnTweets.setVisibility(View.INVISIBLE);
		}
		else {
			mTxtvUrlTitle.setVisibility(View.VISIBLE);
			mTxtvUrl.setVisibility(View.VISIBLE);
			mTxtvUserNameTitle.setVisibility(View.VISIBLE);
			mTxtvUserName.setVisibility(View.VISIBLE);

			mTxtvFavouritesCountTitle.setVisibility(View.VISIBLE);
			mTxtvFavouritesCount.setVisibility(View.VISIBLE);
			mTxtvDescriptionTitle.setVisibility(View.VISIBLE);
			mTxtvDescription.setVisibility(View.VISIBLE);

			mBtnTweets.setVisibility(View.VISIBLE);

			mTxtvUrl.setText(mTwitterUser.getWebsite());
			mTxtvUserName.setText(mTwitterUser.getUserName());
			mTxtvFavouritesCount.setText(mTwitterUser.getFavouritesCount() + "");
			mTxtvDescription.setText(mTwitterUser.getDescription());
		}
	}

	private class DownloadUserInfoTask extends AsyncTask<Void, Void, Void> {

		int mStatusCode = 0;
		String mResultString;
		Exception mConnectionException;

		@Override
		protected Void doInBackground(Void... args) {
			String username = mActxtUsername.getText().toString();
			String encodedUserName= "";
			try {
				encodedUserName= URLEncoder.encode(username, "utf-8");
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			String fetchUrl = "http://api.twitter.com/1/users/show.json?screen_name=" + encodedUserName;

			DefaultHttpClient httpclient = createHttpClient();
			HttpGet httpget = new HttpGet(fetchUrl);

			try {
				HttpResponse response = httpclient.execute(httpget);
				StatusLine statusLine = response.getStatusLine();
				mStatusCode  = statusLine.getStatusCode();

				if (mStatusCode == 200){
					mResultString = EntityUtils.toString(response.getEntity());
				}
			} catch (ClientProtocolException e) {
				e.printStackTrace();
				mConnectionException = e;
			} catch (IOException e) {
				e.printStackTrace();
				mConnectionException = e;
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void arg) {
			mProgressDialog.dismiss();
			if (mStatusCode  == 200){
				saveToDbAndShow(mResultString);
			}
			else if (mStatusCode  == 404){
				Toast.makeText(MainActivity.this, "De gevraagde gebruiker bestaat niet.", Toast.LENGTH_LONG).show();
				mTwitterUser = null;
				updateView();
			}
			else if (mStatusCode > 0){
				Toast.makeText(MainActivity.this, "Er is in verbindingsfout opgetreden met foutcode " + mStatusCode, Toast.LENGTH_LONG).show();
				mTwitterUser = null;
				updateView();
			}
			else {
				Toast.makeText(MainActivity.this, "Gegevens konden niet worden opgehaald. Controleer uw internetverbinding en probeer het opnieuw (" +mConnectionException.toString() + ")" , Toast.LENGTH_LONG).show();
				mTwitterUser = null;
				updateView();
			}
		}
	}

private void downloadOrShowFromDb(String username) {
	TwitterUser twitterUser = null;
	try {
		Dao<TwitterUser, String> twitterUsersDao = getDatabaseHelper().getTwitterUsersDao();
		twitterUser = twitterUsersDao.queryForId(username.toLowerCase());
	} catch (SQLException e) {
		e.printStackTrace();
	}

	if (twitterUser != null && SystemClock.elapsedRealtime() - twitterUser.getLastUpdate() < 1000*60*1){
		mTwitterUser = twitterUser;
		updateView();
	}
	else {
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setMessage("Bezig met het ophalen van gegevens...");
		mProgressDialog.show();
		new DownloadUserInfoTask().execute();
	}
}

	private void saveToDbAndShow(String resultString) {
		mTwitterUser = new TwitterUser(resultString);
		try {
			Dao<TwitterUser, String> twitterUsersDao = getDatabaseHelper().getTwitterUsersDao();
			twitterUsersDao.createOrUpdate(mTwitterUser);
			updateView();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mDatabaseHelper != null) {
			OpenHelperManager.releaseHelper();
			mDatabaseHelper = null;
		}
	}

	private DatabaseHelper getDatabaseHelper() {
		if (mDatabaseHelper == null) {
			mDatabaseHelper = OpenHelperManager.getHelper(this, DatabaseHelper.class);
		}
		return mDatabaseHelper;
	}

}
