import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.squareup.okhttp.*;
import org.codehaus.jettison.json.JSONObject;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class scrapper {
    public static void main(String[] args) {
        System.out.println("START");
        final String jiraUser = "";
        final String jiraPassword = "";
        final String jiraBaseURL = "https://issues.collectionspace.org";
        URI jiraServerUri = null;
        JiraRestClient restClient = null;
        try{
            jiraServerUri = new URI(jiraBaseURL);
            JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
            restClient = factory.create(jiraServerUri,new AnonymousAuthenticationHandler());
           // restClient = factory.createWithBasicHttpAuthentication(jiraServerUri, jiraUser, jiraPassword);
            IssueRestClient issueClient = restClient.getIssueClient();
            SearchRestClient searchClient = restClient.getSearchClient();


            SearchResult resultopen = searchClient.searchJql("reporter='Richard Millet' and status in(Open)").claim();
            Integer openissue = resultopen.getTotal();

            SearchResult resultclose = searchClient.searchJql("reporter='Richard Millet' and status in(Closed)").claim();
            Integer closeissue = resultclose.getTotal();

            //influxdb connection
            String dbName = "jira";
            InfluxDB influxDB = InfluxDBFactory.connect("http://localhost:8086", "root", "root");

            BatchPoints batchPoints = BatchPoints
                    .database(dbName)
                    .tag("async", "true")
                    .retentionPolicy("autogen")
                    .consistency(InfluxDB.ConsistencyLevel.ALL)
                    .build();

            Point point1 = Point.measurement("open")
                    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .addField("OpenIssue", openissue)
                    .build();

            Point point2 = Point.measurement("close")
                    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .addField("ClosedIssue", closeissue)
                    .build();

            batchPoints.point(point1);
            batchPoints.point(point2);

            influxDB.write(batchPoints);


            //Uptime Robot Client
            OkHttpClient client = new OkHttpClient();

            MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
            RequestBody body = RequestBody.create(mediaType, "api_key=u508431-1dbdee24b5c1dac6f4d16322&format=json&logs=1");
            Request request = new Request.Builder()
                    .url("https://api.uptimerobot.com/v2/getMonitors")
                    .post(body)
                    .addHeader("content-type", "application/x-www-form-urlencoded")
                    .addHeader("cache-control", "no-cache")
                    .build();

            Response response = client.newCall(request).execute();
            int i = response.toString().indexOf("{");
            String str = response.toString().substring(i);
            str = str.replaceAll("/", "");
            str = str.replaceAll(":", "");
            JSONObject obj = new JSONObject(str.trim());
            System.out.println("Uptime: " +obj.getString("message"));

         //InfluxDb Write
            Point point3 = Point.measurement("uptime")
                    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .addField("msg", obj.getString("message"))
                    .build();

            batchPoints.point(point3);

            influxDB.write(batchPoints);


            Query query = new Query("SELECT * FROM uptime", dbName);
            QueryResult queryResult = influxDB.query(query);

            for (QueryResult.Result count : queryResult.getResults()) {

                System.out.println("Uptime Trend: " +count.toString());
            }

        }catch(Exception e){
            e.printStackTrace();
        }finally{
            try {
                restClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("END");
    }

}
