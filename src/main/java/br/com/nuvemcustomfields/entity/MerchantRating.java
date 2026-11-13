package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="merchant_ratings")
public class MerchantRating {
    @Id @Column(length=36) private String id;
    @Column(name="store_id",nullable=false) private Long storeId;
    @Column(name="displayed_at",nullable=false) private Instant displayedAt;
    private Short rating;
    @Column(columnDefinition="TEXT") private String comment;
    @Column(name="rated_at") private Instant ratedAt;
    @Column(name="dismissed_at") private Instant dismissedAt;
    @Column(name="public_review_clicked_at") private Instant publicReviewClickedAt;
    protected MerchantRating() {}
    public MerchantRating(long storeId,Instant now) {id=UUID.randomUUID().toString();this.storeId=storeId;displayedAt=now;}
    public String getId(){return id;} public Long getStoreId(){return storeId;}
    public Instant getDisplayedAt(){return displayedAt;} public Integer getRating(){return rating==null?null:rating.intValue();}
    public String getComment(){return comment;} public Instant getRatedAt(){return ratedAt;}
    public Instant getDismissedAt(){return dismissedAt;}
    public Instant getPublicReviewClickedAt(){return publicReviewClickedAt;}
    public void markPublicReviewClicked(Instant now){if(publicReviewClickedAt==null)publicReviewClickedAt=now;}
    public void rate(int score,String text,Instant now){rating=(short)score;comment=text;ratedAt=now;}
    public void dismiss(Instant now){if(dismissedAt==null)dismissedAt=now;}
}
