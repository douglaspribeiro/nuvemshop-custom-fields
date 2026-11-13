package br.com.nuvemcustomfields.controller;

import java.time.Instant;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BackofficeRatingsController {
    private final JdbcTemplate jdbc;
    public BackofficeRatingsController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Rating(String id,long storeId,String storeName,String plan,int score,String comment,
            Instant displayedAt,Instant ratedAt,Instant publicReviewClickedAt) {
        public String stars(){return "★".repeat(score)+"☆".repeat(5-score);}
    }
    @GetMapping("/backoffice/ratings")
    public String page(@RequestParam(required=false) Integer rating,@RequestParam(required=false) Long storeId,Model model){
        if(rating!=null && (rating<1 || rating>5) || storeId!=null && storeId<=0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String filter="r.rating is not null and r.rated_at is not null";var args=new ArrayList<Object>();
        if(rating!=null){filter+=" and r.rating=?";args.add(rating);}
        if(storeId!=null){filter+=" and r.store_id=?";args.add(storeId);}
        var ratings=jdbc.query("select r.*,s.store_name,s.plan from merchant_ratings r left join stores s on s.store_id=r.store_id where "
                +filter+" order by r.rated_at desc,r.id desc limit 100",(row,index)->new Rating(row.getString("id"),row.getLong("store_id"),
                row.getString("store_name"),row.getString("plan"),row.getInt("rating"),row.getString("comment"),
                row.getTimestamp("displayed_at").toInstant(),row.getTimestamp("rated_at").toInstant(),
                row.getTimestamp("public_review_clicked_at")==null?null:row.getTimestamp("public_review_clicked_at").toInstant()),args.toArray());
        model.addAttribute("ratings",ratings);model.addAttribute("selectedRating",rating);model.addAttribute("selectedStoreId",storeId);
        return "backoffice/ratings";
    }
}
