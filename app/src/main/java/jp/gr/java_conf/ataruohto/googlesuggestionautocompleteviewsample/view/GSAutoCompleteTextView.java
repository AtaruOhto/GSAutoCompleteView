package jp.gr.java_conf.ataruohto.googlesuggestionautocompleteviewsample.view;

/**
 * Created by ataru on 2015/07/12.
 */
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Xml;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

/**
 * Created by ataru on 2015/07/11.
 */

public class GSAutoCompleteTextView extends AutoCompleteTextView {

    public GSAutoCompleteTextView(Context context) {
        super(context);
        setTextChangedListener();
    }

    public GSAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTextChangedListener();
    }

    public GSAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTextChangedListener();
    }

    private void setTextChangedListener() {
        this.addTextChangedListener(new keywordTextWatcher(new keywordTextWatcher.CallbackAfterTextChanged() {

            @Override
            public void doAfterTextChanged(ArrayList<String> suggestions) {
                setUpGSAdapter(suggestions);
            }

        }));
    }

    private void setUpGSAdapter(ArrayList<String> suggestions) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1, suggestions);
        this.setAdapter(adapter);
        this.setThreshold(1);
        adapter.notifyDataSetChanged();
    }

    private static class keywordTextWatcher implements TextWatcher {

        //Some devive have bug :callbacks are called twice times. So I set a store variable here for comparison.
        private String comparisonString;
        private CallbackAfterTextChanged callback = null;

        public interface CallbackAfterTextChanged {
            void doAfterTextChanged(ArrayList<String> suggestions);
        }

        public keywordTextWatcher(CallbackAfterTextChanged callbackAfterTextChanged) {
            this.callback = callbackAfterTextChanged;
        }

        public void afterTextChanged(Editable s) {

            if (s.length() == 0) {
                return;
            }

            if (comparisonString == null || !(comparisonString.equals(s.toString()))) {
                new GSAutoCompleteTask(new GSAutoCompleteTask.GSTaskCallback() {

                    @Override
                    public void postExecute(ArrayList<String> suggestions) {
                        callback.doAfterTextChanged(suggestions);
                    }

                }).execute(s.toString());
            }

            comparisonString = s.toString();
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private static class GSAutoCompleteTask extends AsyncTask<String, Void, ArrayList<String>> {

        GSTaskCallback gsTaskCallback = null;

        public interface GSTaskCallback {
            void postExecute(ArrayList<String> list);
        }

        public GSAutoCompleteTask(GSTaskCallback callback) {
            this.gsTaskCallback = callback;
        }

        @Override
        protected ArrayList<String> doInBackground(String... param) {

            String keywordWillBePostedToGSAPI = param[0];
            final String SCHEME = "http";
            final String AUTHORITY = "google.com";
            final String PATH = "/complete/search";

            Uri.Builder builder = new Uri.Builder();
            builder.scheme(SCHEME);
            builder.encodedAuthority(AUTHORITY);
            builder.path(PATH);
            builder.appendQueryParameter("output", "toolbar");
            builder.appendQueryParameter("q", keywordWillBePostedToGSAPI);

            HttpGet request = new HttpGet(builder.build().toString());
            DefaultHttpClient httpClient = new DefaultHttpClient();

            try {
                String GSAPIResult = httpClient.execute(request, new ResponseHandler<String>() {
                    @Override
                    public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {

                        switch (response.getStatusLine().getStatusCode()) {
                            case HttpStatus.SC_OK:
                                return EntityUtils.toString(response.getEntity(), "UTF-8");

                            case HttpStatus.SC_NOT_FOUND:
                                throw new RuntimeException("RuntimeException occurred! HttpStatus.SC_NOT_FOUND");

                            default:
                                throw new RuntimeException("RuntimeException occurred! Not a valid status");
                        }

                    }
                });

                return getGSList(GSAPIResult);

            } catch (ClientProtocolException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                httpClient.getConnectionManager().shutdown();
            }
        }

        private ArrayList<String> getGSList(String suggestionsXMLText) {

            final String targetTagName = "suggestion";
            final String targetAttr = "data";
            ArrayList<String> GSList = new ArrayList<>();

            XmlPullParser parser = Xml.newPullParser();

            try {
                parser.setInput(new StringReader(suggestionsXMLText));
            } catch (XmlPullParserException e) {
                throw new RuntimeException("RuntimeException caused by XmlPullParserException");
            }

            try {
                int eventType;
                eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {

                    if (eventType == XmlPullParser.START_TAG) {

                        if (parser.getName().equalsIgnoreCase(targetTagName)) {
                            String suggestion = parser.getAttributeValue(null, targetAttr);
                            GSList.add(suggestion);
                        }

                    }

                    eventType = parser.next();
                }
            } catch (Exception e) {
                throw new RuntimeException("RuntimeException during parsing XML from GS");
            }
            return GSList;
        }

        @Override
        protected void onPostExecute(ArrayList<String> resultList) {
            gsTaskCallback.postExecute(resultList);
            super.onPostExecute(resultList);
        }

    }
}

