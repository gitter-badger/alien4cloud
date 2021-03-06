package alien4cloud.tosca.properties.constraints;

import java.util.List;

import org.elasticsearch.common.collect.Lists;
import org.junit.Test;

import alien4cloud.model.components.IncompatiblePropertyDefinitionException;
import alien4cloud.model.components.PropertyConstraint;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.components.constraints.EqualConstraint;
import alien4cloud.model.components.constraints.InRangeConstraint;
import alien4cloud.model.components.constraints.LessThanConstraint;
import alien4cloud.tosca.normative.ToscaType;
import alien4cloud.tosca.properties.constraints.exception.ConstraintViolationException;

public class PropertyDefinitionConstraintsTest {

    @Test
    public void testCheckIfCompatibleOrFailConstraintSatisfiedAllNull() throws ConstraintViolationException, IncompatiblePropertyDefinitionException {
        PropertyDefinition propDef1 = new PropertyDefinition();
        PropertyDefinition propDef2 = new PropertyDefinition();
        propDef1.setType(ToscaType.STRING.toString());
        propDef2.setType(ToscaType.STRING.toString());
        propDef1.checkIfCompatibleOrFail(propDef2);
    }

    @Test(expected = IncompatiblePropertyDefinitionException.class)
    public void testCheckIfCompatibleOrFailConstraintNotSatisfiedNull() throws ConstraintViolationException, IncompatiblePropertyDefinitionException {
        PropertyDefinition propDef1 = new PropertyDefinition();
        PropertyDefinition propDef2 = new PropertyDefinition();
        propDef1.setType(ToscaType.STRING.toString());
        propDef2.setType(ToscaType.STRING.toString());

        List<PropertyConstraint> constraints = Lists.newArrayList();
        constraints.add(new EqualConstraint());
        propDef1.setConstraints(constraints);

        EqualConstraint constraint = new EqualConstraint();
        constraint.setEqual("test");
        constraints.add(constraint);
        propDef1.setConstraints(constraints);

        propDef1.checkIfCompatibleOrFail(propDef2);
    }

    @Test
    public void testCheckIfCompatibleOrFailConstraintSatisfiedSetOfPropertyConstraint() throws ConstraintViolationException,
            IncompatiblePropertyDefinitionException {
        PropertyDefinition propDef1 = new PropertyDefinition();
        PropertyDefinition propDef2 = new PropertyDefinition();
        List<PropertyConstraint> constraints = Lists.newArrayList();
        propDef1.setType(ToscaType.STRING.toString());
        propDef2.setType(ToscaType.STRING.toString());

        EqualConstraint constraint1 = new EqualConstraint();
        constraint1.setEqual("test");
        constraints.add(constraint1);

        LessThanConstraint constraint2 = new LessThanConstraint();
        constraint2.setLessThan("5");
        constraints.add(constraint2);

        propDef1.setConstraints(constraints);
        propDef2.setConstraints(constraints);

        propDef1.checkIfCompatibleOrFail(propDef2);
    }

    @Test
    public void testCheckIfCompatibleOrFailConstraintSatisfiedSetOfScamblePropertyConstraint() throws ConstraintViolationException,
            IncompatiblePropertyDefinitionException {
        PropertyDefinition propDef1 = new PropertyDefinition();
        PropertyDefinition propDef2 = new PropertyDefinition();
        List<PropertyConstraint> constraintsProp1 = Lists.newArrayList();
        List<PropertyConstraint> constraintsProp2 = Lists.newArrayList();
        propDef1.setType(ToscaType.STRING.toString());
        propDef2.setType(ToscaType.STRING.toString());

        EqualConstraint constraint11 = new EqualConstraint();
        constraint11.setEqual("test");

        LessThanConstraint constraint12 = new LessThanConstraint();
        constraint12.setLessThan("5");

        constraintsProp1.add(constraint11);
        constraintsProp1.add(constraint12);

        EqualConstraint constraint21 = new EqualConstraint();
        constraint21.setEqual("test");

        LessThanConstraint constraint22 = new LessThanConstraint();
        constraint22.setLessThan("5");

        constraintsProp2.add(constraint22);
        constraintsProp2.add(constraint21);

        propDef1.setConstraints(constraintsProp1);
        propDef2.setConstraints(constraintsProp2);

        propDef1.checkIfCompatibleOrFail(propDef2);
    }

    @Test(expected = IncompatiblePropertyDefinitionException.class)
    public void testCheckIfCompatibleOrFailConstraintNotSatisfiedSetOfScamblePropertyConstraintWithDifferentValues() throws ConstraintViolationException,
            IncompatiblePropertyDefinitionException {
        PropertyDefinition propDef1 = new PropertyDefinition();
        PropertyDefinition propDef2 = new PropertyDefinition();
        List<PropertyConstraint> constraintsProp1 = Lists.newArrayList();
        List<PropertyConstraint> constraintsProp2 = Lists.newArrayList();
        propDef1.setType(ToscaType.STRING.toString());
        propDef2.setType(ToscaType.INTEGER.toString());

        EqualConstraint constraint1 = new EqualConstraint();
        constraint1.setEqual("test");

        EqualConstraint constraint1Bis = new EqualConstraint();
        constraint1Bis.setEqual("testShoulFailed");

        LessThanConstraint constraint2 = new LessThanConstraint();
        constraint2.setLessThan("5");

        constraintsProp1.add(constraint1);
        constraintsProp1.add(constraint2);

        constraintsProp2.add(constraint2);
        constraintsProp2.add(constraint1Bis);

        propDef1.setConstraints(constraintsProp1);
        propDef2.setConstraints(constraintsProp2);

        propDef1.checkIfCompatibleOrFail(propDef2);
    }

    @Test(expected = IncompatiblePropertyDefinitionException.class)
    public void testCheckIfCompatibleOrFailConstraintNotSatisfiedSetOfScamblePropertyConstraintWithDifferentType() throws ConstraintViolationException,
            IncompatiblePropertyDefinitionException {
        PropertyDefinition propDef1 = new PropertyDefinition();
        PropertyDefinition propDef2 = new PropertyDefinition();
        List<PropertyConstraint> constraintsProp1 = Lists.newArrayList();
        List<PropertyConstraint> constraintsProp2 = Lists.newArrayList();
        propDef1.setType(ToscaType.STRING.toString());
        propDef2.setType(ToscaType.INTEGER.toString());

        EqualConstraint constraint1 = new EqualConstraint();
        constraint1.setEqual("test");

        InRangeConstraint constraint1Bis = new InRangeConstraint();
        constraint1Bis.setRangeMaxValue("4");

        LessThanConstraint constraint2 = new LessThanConstraint();
        constraint2.setLessThan("5");

        constraintsProp1.add(constraint1);
        constraintsProp1.add(constraint2);

        constraintsProp2.add(constraint2);
        constraintsProp2.add(constraint1Bis);

        propDef1.setConstraints(constraintsProp1);
        propDef2.setConstraints(constraintsProp2);

        propDef1.checkIfCompatibleOrFail(propDef2);
    }
}