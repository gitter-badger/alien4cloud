package alien4cloud.model.topology;

import java.util.Map;

import alien4cloud.utils.jackson.ConditionalAttributes;
import alien4cloud.utils.jackson.ConditionalOnAttribute;
import alien4cloud.utils.jackson.JSonMapEntryArrayDeSerializer;
import alien4cloud.utils.jackson.JSonMapEntryArraySerializer;
import lombok.Getter;
import lombok.Setter;
import alien4cloud.json.deserializer.PropertyValueDeserializer;
import alien4cloud.model.components.AbstractPropertyValue;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * <p>
 * Specify a relationship between the components of the cloud application.
 * </p>
 * <p>
 * For each specified Relationship Template the source element and target element MUST be specified in the Topology Template.
 * </p>
 * 
 * @author luc boutier
 */
@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
public class RelationshipTemplate extends AbstractTemplate {
    /**
     * <p>
     * This element specifies the target of the relationship represented by the current Relationship Template
     * </p>
     * <p>
     * This attribute references by ID a Node Template or a Capability of a Node Template within the same Service Template document that is the target of the
     * Relationship Template.
     * </p>
     * <p>
     * If the Relationship Type referenced by the type attribute defines a constraint on the valid source of the relationship by means of its ValidTarget
     * element, the ref attribute of TargetElement MUST reference an object the type of which complies with the valid source constraint of the respective
     * Relationship Type.
     * </p>
     * <p>
     * In case a Node Type is defined as valid target in the Relationship Type definition, the ref attribute MUST reference a Node Template of the corresponding
     * Node Type (or of a sub-type).
     * </p>
     * <p>
     * In case a Capability Type is defined a valid target in the Relationship Type definition, the ref attribute MUST reference a Capability of the
     * corresponding Capability Type within a Node Template.
     * </p>
     */
    private String target;

    private String requirementName;
    private String requirementType;

    private String targetedCapabilityName;

    /**
     * Properties of the relationship template
     */
    @ConditionalOnAttribute(ConditionalAttributes.REST)
    @JsonDeserialize(using = JSonMapEntryArrayDeSerializer.class, contentUsing = PropertyValueDeserializer.class)
    @JsonSerialize(using = JSonMapEntryArraySerializer.class)
    private Map<String, AbstractPropertyValue> properties;
}