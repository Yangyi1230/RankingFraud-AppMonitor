import BasicData.AppData;
import BasicData.Review;
import Controller.AppInfoController;
import Controller.DbController;
import Pipeline.AppStorePaidRankPipeline;
import Pipeline.FloatUpRankPipeline;
import Processor.AppStorePaidRankProcessor;
import Processor.FloatUpRankPageProcessor;
import Processor.ProxyProcessor;
import Processor.ReviewPageProcessor;
import Utils.Toolkit;
import us.codecraft.webmagic.Spider;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;


/**
 * Created by chenhao on 2/18/16.
 */
public class DataCrawler {

    public static AppStorePaidRankProcessor appStorePaidRankProcessor = new AppStorePaidRankProcessor();
    public static FloatUpRankPageProcessor floatUpRankPageProcessor = new FloatUpRankPageProcessor();
    public static ReviewPageProcessor reviewPageProcessor;
    public static ProxyProcessor proxyProcessor = new ProxyProcessor();
    public static AppInfoController appInfoController = new AppInfoController();
    public static DbController dbController = new DbController();

    public static void main(String args[]) {

        Spider.create(appStorePaidRankProcessor)
                .addUrl(AppStorePaidRankProcessor.PAGE_URL)
                .addPipeline(new AppStorePaidRankPipeline(appInfoController))
                .thread(1)
                .run();

        Spider.create(floatUpRankPageProcessor)
                .addUrl(FloatUpRankPageProcessor.PAGE_URL)
                .addPipeline(new FloatUpRankPipeline(appInfoController))
                .thread(1)
                .run();


        dbController.setInsertAppInfoPst(DbController.insertAppInfoSql);

        List<AppData> dataList = appInfoController.fetchAppInfo();
        if (dataList != null) {
            for (AppData appData : dataList) {
                System.out.println(appData.ranking + "  " + appData.rankType + " " + appData.id + "  " + "  " + appData.averageUserRating + "  " + appData.userRatingCount + "  "
                        + appData.userRatingCountForCurrentVersion + "  " + appData.getScrapeTime());
                try {
                    dbController.insertAppInfoPst.setString(1, appData.getId());
                    dbController.insertAppInfoPst.setString(2, appData.getRankType());
                    dbController.insertAppInfoPst.setInt(3, appData.ranking);
                    dbController.insertAppInfoPst.setDouble(4, appData.averageUserRating);
                    dbController.insertAppInfoPst.setDouble(5, appData.averageUserRatingForCurrentVersion);
                    dbController.insertAppInfoPst.setDouble(6, appData.userRatingCount);
                    dbController.insertAppInfoPst.setDouble(7, appData.userRatingCountForCurrentVersion);
                    dbController.insertAppInfoPst.setDate(8, new java.sql.Date(appData.getScrapeTime().getTime()));
                    dbController.insertAppInfoPst.executeUpdate();
                } catch (SQLException e) {
                    System.out.println("duplicate app, skip it");
                }
            }
        }


        proxyProcessor.setScrapePageCount(10);
        Spider.create(proxyProcessor)
                .addUrl(proxyProcessor.INITIAL_URL)
                .thread(3)
                .run();


        List appIdList = appInfoController.getAppIdList();
        appIdList = Toolkit.removeDuplicate(appIdList);

        for (Object id : appIdList) {
            reviewPageProcessor = new ReviewPageProcessor(id.toString());
            reviewPageProcessor.setProxyList(proxyProcessor.getProxyList());
            Spider.create(reviewPageProcessor)
                    .addUrl(ReviewPageProcessor.INITIAL_URL)
                    .thread(5)
                    .run();

            dbController.setInsertReviewPst(DbController.insertReviewSql);
            dbController.setInsertAuthorPst(DbController.insertAuthorSql);

            Set<Review> reviewSet = reviewPageProcessor.getReviewSet();
            for (Review review : reviewSet) {
                try {
                    dbController.insertReviewPst.setString(1, review.getId());
                    dbController.insertReviewPst.setString(2, review.getAuthorId());
                    dbController.insertReviewPst.setDouble(3, review.getRate());
                    dbController.insertReviewPst.setString(4, review.getVersion());
                    dbController.insertReviewPst.setDate(5, new java.sql.Date(review.getDate().getTime()));
                    dbController.insertReviewPst.executeUpdate();

                    dbController.insertAuthorPst.setString(1, review.getAuthorId());
                    dbController.insertAuthorPst.setString(2, review.getAuthorId());
                    dbController.insertAuthorPst.executeLargeUpdate();

                } catch (SQLException e) {
                    System.out.println("duplicate one, skip it");
                }
            }
        }

    }

}
