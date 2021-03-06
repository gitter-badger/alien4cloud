package alien4cloud.model.deployment;

import java.util.Date;

import alien4cloud.paas.model.DeploymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.elasticsearch.annotation.ESObject;
import org.elasticsearch.annotation.Id;
import org.elasticsearch.annotation.NestedObject;
import org.elasticsearch.annotation.StringField;
import org.elasticsearch.annotation.TimeStamp;
import org.elasticsearch.annotation.query.TermFilter;
import org.elasticsearch.mapping.IndexType;

import alien4cloud.model.application.DeploymentSetup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents a deployment in ALIEN.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
@SuppressWarnings("PMD.UnusedPrivateField")
@ESObject
public class Deployment {

    /** Id of the deployment */
    @Id
    private String id;

    @TermFilter
    @StringField(indexType = IndexType.not_analyzed)
    private DeploymentSourceType sourceType;

    /** Id of the cloud on which the deployment is performed. */
    @TermFilter
    @StringField(indexType = IndexType.not_analyzed, includeInAll = false)
    private String cloudId;

    /** Id of the application that has been deployed */
    @TermFilter
    @StringField(indexType = IndexType.not_analyzed, includeInAll = false)
    private String sourceId;

    /** Name of the application. This is used as backup if application is deleted. */
    @TermFilter
    @StringField(indexType = IndexType.not_analyzed)
    private String sourceName;

    /** Id of the topology that is deployed (runtime topology) */
    @TermFilter
    @StringField(indexType = IndexType.not_analyzed, includeInAll = false)
    private String topologyId;

    /** Start date of the deployment */
    @TimeStamp(format = "")
    private Date startDate;

    /** End date of the deployment. */
    @TermFilter
    private Date endDate;

    /** The current status of the deployment. */
    private DeploymentStatus deploymentStatus;

    /** Linked deployment setup */
    @NestedObject
    private DeploymentSetup deploymentSetup;
}