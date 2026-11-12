package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="feature_requests")
public class FeatureRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long storeId;
    @Column(nullable=false,length=160) private String title;
    @Column(nullable=false,length=4000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PlanType planAtSubmission;
    @Column(nullable=false,length=36,unique=true) private String submissionToken;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30)
    @org.hibernate.annotations.ColumnDefault("'RECEIVED'")
    private FeatureRequestStatus status=FeatureRequestStatus.RECEIVED;
    @Column(nullable=false) @org.hibernate.annotations.ColumnDefault("0") private long revision;
    @OneToMany(mappedBy="request",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("createdAt ASC, id ASC")
    @org.hibernate.annotations.BatchSize(size=50)
    private java.util.List<FeatureRequestUpdate> updates=new java.util.ArrayList<>();
    private Instant discordSentAt;
    @Column(nullable=false) private Instant nextNotificationAt=Instant.now();
    @Column(nullable=false) private int notificationAttempts;
    public Long getId(){return id;}
    public FeatureRequestStatus getStatus(){return status;} public void setStatus(FeatureRequestStatus v){status=v;}
    public long getRevision(){return revision;} public void setRevision(long v){revision=v;}
    public java.util.List<FeatureRequestUpdate> getUpdates(){return updates;}
    public Long getStoreId(){return storeId;} public void setStoreId(Long v){storeId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public PlanType getPlanAtSubmission(){return planAtSubmission;} public void setPlanAtSubmission(PlanType v){planAtSubmission=v;}
    public String getSubmissionToken(){return submissionToken;} public void setSubmissionToken(String v){submissionToken=v;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getDiscordSentAt(){return discordSentAt;} public void setDiscordSentAt(Instant v){discordSentAt=v;}
    public Instant getNextNotificationAt(){return nextNotificationAt;} public void setNextNotificationAt(Instant v){nextNotificationAt=v;}
    public int getNotificationAttempts(){return notificationAttempts;} public void setNotificationAttempts(int v){notificationAttempts=v;}
}
