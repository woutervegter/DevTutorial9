package me.moop.mytwitter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.ArrayList;

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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class TweetsActivity extends Activity {

	TextView mTxtvTitle;

	ProgressDialog mProgressDialog;
	ArrayList<Tweet> mTweets;
	ListView mLvTweets;

	DatabaseHelper mDatabaseHelper;

	TwitterUser mTwitterUser;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tweets);

		mTxtvTitle = (TextView) findViewById(R.id.txtvTitle);
		mLvTweets = (ListView) findViewById(R.id.lvTweets);

		Intent intent = getIntent();
		Bundle extrasBundle = intent.getExtras();
		String userName = extrasBundle.getString("twitter_user_name");
		try {
			Dao<TwitterUser, String> twitterUsersDao = getDatabaseHelper().getTwitterUsersDao(); 
			mTwitterUser = twitterUsersDao.queryForId(userName.toLowerCase());
		} catch (SQLException e) {
			e.printStackTrace();
		}

		updateView();

		downloadOrShowFromDb();
	}

	private void updateView() {
		mTxtvTitle.setText(mTwitterUser.getUserName());

		if (mTweets != null){
			TweetsListAdapter tweetsListAdapter = new TweetsListAdapter(this, mTweets);
			mLvTweets.setAdapter(tweetsListAdapter);
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

	private class DownloadTweetsTask extends AsyncTask<Void, Void, Void> {

		int mStatusCode = 0;
		String mResultString;
		Exception mConnectionException;

		@Override
		protected Void doInBackground(Void... args) {
			String encodedUserName= "";
			try {
				encodedUserName= URLEncoder.encode(mTwitterUser.getUserName(), "utf-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}

			String fetchUrl = "http://api.twitter.com/1/statuses/user_timeline.json?include_rts=true&count=30&screen_name=" + encodedUserName;

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
			else if (mStatusCode  == 401){
				Toast.makeText(TweetsActivity.this, "De timeline van deze gebruiker is niet publiek toegankelijk.", Toast.LENGTH_LONG).show();
			}
			else if (mStatusCode > 0){
				Toast.makeText(TweetsActivity.this, "Er is in verbindingsfout opgetreden met foutcode " + mStatusCode, Toast.LENGTH_LONG).show();
			}
			else {
				Toast.makeText(TweetsActivity.this, "Gegevens konden niet worden opgehaald. Controleer uw internetverbinding en probeer het opnieuw (" +mConnectionException.toString() + ")" , Toast.LENGTH_LONG).show();
			}
		}

	}
	
	private void downloadOrShowFromDb() {
		if (SystemClock.elapsedRealtime() - mTwitterUser.getLastTweetsUpdate() < 1000*60*1){
			mTweets = new ArrayList<Tweet>();
			mTweets.addAll(mTwitterUser.getTweets());
			updateView();
		} else{
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setMessage("Bezig met het ophalen van gegevens...");
			mProgressDialog.show();
			new DownloadTweetsTask().execute();
		}
	}

	private void saveToDbAndShow(String resultString) {
		try {
			Dao<Tweet, Long> tweetsDao = getDatabaseHelper().getTweetsDao();
			tweetsDao.delete(mTwitterUser.getTweets());
			
			mTweets = new ArrayList<Tweet>();
			JSONArray jSONArray = new JSONArray(resultString);
			for (int counter = 0; counter < jSONArray.length() ; counter ++){
				JSONObject jSONObject = jSONArray.getJSONObject(counter);
				Tweet tweet = new Tweet(jSONObject);
				mTweets.add(tweet);
				
				tweet.setTwitterUser(mTwitterUser);
				tweetsDao.create(tweet);
			}
			
			Dao<TwitterUser, String> twitterUsersDao = getDatabaseHelper().getTwitterUsersDao();
			mTwitterUser.setLastTweetsUpdate(SystemClock.elapsedRealtime());
			twitterUsersDao.update(mTwitterUser);

			updateView();
		} catch (JSONException e) {
			e.printStackTrace();
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