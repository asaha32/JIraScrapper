import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
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


            SearchResult result = searchClient.searchJql("reporter='Richard Millet' and status in(Open)").claim();
            Integer openissue = result.getTotal();

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
                    .tag("status", "issuecount") // tag the individual point
                    .build();

            batchPoints.point(point1);

            influxDB.write(batchPoints);

            Query query = new Query("SELECT * FROM open", dbName);
            QueryResult queryResult = influxDB.query(query);

            for (QueryResult.Result count : queryResult.getResults()) {

                System.out.println("Open Issues: " +count.toString());
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
