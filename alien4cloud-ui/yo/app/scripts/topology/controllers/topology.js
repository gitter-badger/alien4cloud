/* global UTILS, jsyaml */

'use strict';

angular.module('alienUiApp').controller('TopologyCtrl', ['alienAuthService', '$scope', '$modal', 'topologyJsonProcessor', 'topologyServices', 'resizeServices', '$q', '$translate', '$upload', 'componentService', 'nodeTemplateService', '$timeout', 'applicationVersionServices', 'appVersions', 'topologyId', 'toscaService', 'toscaCardinalitiesService', '$window',
  function(alienAuthService, $scope, $modal, topologyJsonProcessor, topologyServices, resizeServices, $q, $translate, $upload, componentService, nodeTemplateService, $timeout, applicationVersionServices, appVersions, topologyId, toscaService, toscaCardinalitiesService, $window) {
    $scope.view = 'RENDERED';

    $scope.activeDisplays = [];

    $scope.displays = {
      displayTopology: {
        active: false
      },
      displayAddNode: {
        active: false
      },
      displayDependencies: {
        active: false
      },
      displayInputs: {
        active: false
      },
      displayDetailsComponent: {
        active: false
      }
    };

    var disableAllDisplayExcept = function(displays) {
      $scope.activeDisplays = displays;
      for (var displayName in $scope.displays) {
        if ($scope.displays.hasOwnProperty(displayName)) {
          $scope.displays[displayName].active = UTILS.arrayContains($scope.activeDisplays, displayName);
        }
      }
    };

    $scope.setDisplay = function(displayName, active) {
      if ($scope.displays[displayName].active !== active) {
        $scope.toggleDisplay(displayName);
      }
    };

    $scope.toggleDisplay = function(displayName) {
      $scope.displays[displayName].active = !$scope.displays[displayName].active;
      // Specific rules for displays which are logically linked
      if ($scope.displays[displayName].active) {
        $scope.activeDisplays.push(displayName);
        switch (displayName) {
          case 'displayAddNode':
            disableAllDisplayExcept(['displayTopology', 'displayAddNode']);
            break;
          case 'displayDependencies':
            disableAllDisplayExcept(['displayTopology', 'displayDependencies']);
            break;
          case 'displayInputs':
            if (!$scope.displays['displayDetailsComponent'].active) {
              disableAllDisplayExcept(['displayTopology', 'displayInputs']);
            } else {
              disableAllDisplayExcept(['displayDetailsComponent', 'displayInputs']);
            }
            break;
          case 'displayDetailsComponent':
            if (!$scope.displays['displayInputs'].active) {
              disableAllDisplayExcept(['displayTopology', 'displayDetailsComponent']);
            } else {
              disableAllDisplayExcept(['displayDetailsComponent', 'displayInputs']);
            }
            break;
        }
      } else {
        UTILS.arrayRemove($scope.activeDisplays, displayName);
      }
      calculateDisplay();
    };

    var calculateDisplay = function() {
      var maxDisplay = 2;
      var numberOfColumn = 12;
      // How many display that is asked to display
      if ($scope.activeDisplays.length > maxDisplay) {
        while ($scope.activeDisplays.length > maxDisplay) {
          var removedDisplay = $scope.activeDisplays.shift();
          $scope.displays[removedDisplay].active = false;
        }
      }
      // If only one display is asked to be displayed, we show also the topology
      // If no display is asked to be displayed, we show at least the topology
      if (($scope.activeDisplays.length === 1 && $scope.activeDisplays[0] !== 'displayTopology') ||
        $scope.activeDisplays.length === 0) {
        $scope.displays.displayTopology.active = true;
        $scope.activeDisplays.push('displayTopology');
      }

      for (var displayName in $scope.displays) {
        if ($scope.displays.hasOwnProperty(displayName) && $scope.displays[displayName].active) {
          $scope.displays[displayName].size = numberOfColumn / $scope.activeDisplays.length;
        }
      }
    };
    calculateDisplay();

    // TODO : when topology templates edition with use also version, remove this IF statement
    if (UTILS.isDefinedAndNotNull(appVersions)) {
      // default version loading
      $scope.appVersions = appVersions;
      $scope.selectedVersion = $scope.appVersions[0];
      $scope.topologyId = $scope.selectedVersion.topologyId;
    } else {
      // TODO : remove this part when apVersion will be given in state 'topologytemplates.detail.topology'
      $scope.appVersions = appVersions;
      // $scope.selectedVersion = null;
      $scope.topologyId = topologyId;
    }

    $scope.editorContent = '';
    var outputKeys = ['outputProperties', 'outputAttributes', 'inputArtifacts'];
    var regexPatternn = '^[\\w_]*$';

    // Size management
    var borderSpacing = 10;
    var border = 2;
    var detailDivWidth = 450;
    var widthOffset = detailDivWidth + (3 * borderSpacing) + (2 * border);
    var heightOffset = 156;

    function onResize(width, height) {
      $scope.dimensions = {
        width: width,
        height: height
      };
      $scope.$apply();
    }

    resizeServices.register(onResize, widthOffset, heightOffset);

    $scope.dimensions = {
      height: resizeServices.getHeight(heightOffset),
      width: resizeServices.getWidth(widthOffset)
    };
    // end size management

    var COMPUTE_TYPE = 'tosca.nodes.Compute';

    var setSelectedVersionByName = function(name) {
      $scope.selectedVersionName = name;
      for (var i = 0; i < $scope.appVersions.length; i++) {
        if ($scope.appVersions[i].version === $scope.selectedVersionName) {
          $scope.selectedVersion = $scope.appVersions[i];
          break;
        }
      }
    };

    var updateSelectedVersionName = function(applicationVersion) {
      if (UTILS.isDefinedAndNotNull(applicationVersion)) {
        applicationVersionServices.getFirst({
          applicationId: $scope.application.id
        }, function updateSelectedVersion(result) {
          $scope.selectedVersionName = result.data.version;
          setSelectedVersionByName($scope.selectedVersionName);
        });
      }
    };
    updateSelectedVersionName($scope.appVersions);

    $scope.changeVersion = function(selectedVersion) {
      setSelectedVersionByName(selectedVersion.version);
      $scope.topologyId = selectedVersion.topologyId;
      topologyServices.dao.get({
        topologyId: $scope.topologyId
      }, function(successResult) {
        refreshTopology(successResult.data);
      });
      selectTab('topology-components-search');
    };

    var refreshTopology = function(topologyDTO, selectedNodeTemplate) {
      for (var nodeId in topologyDTO.topology.nodeTemplates) {
        if (topologyDTO.topology.nodeTemplates.hasOwnProperty(nodeId)) {
          topologyDTO.topology.nodeTemplates[nodeId].name = nodeId;
        }
      }
      $scope.topology = topologyDTO;

      // enrich objects to add maps for the fields that are currently mapped as array of map entries.
      topologyJsonProcessor.process($scope.topology);

      fillBounds($scope.topology.topology);
      initOutputs($scope.topology.topology);
      var topologyInputs = $scope.topology.topology.inputs;
      if (UTILS.isDefinedAndNotNull(topologyInputs)) {
        for (var inputId in topologyInputs) {
          if (topologyInputs.hasOwnProperty(inputId)) {
            topologyInputs[inputId].inputId = inputId;
          }
        }
      }
      $scope.editorContent = jsyaml.safeDump($scope.topology.topology);
      if (UTILS.isDefinedAndNotNull(selectedNodeTemplate)) {
        fillNodeSelectionVars($scope.topology.topology.nodeTemplates[selectedNodeTemplate]);
      } else {
        $scope.selectedNodeTemplate = null;
      }
    };

    // Topology can comes from application OR topology template
    topologyServices.dao.get({
      topologyId: $scope.topologyId
    }, function(successResult) {
      refreshTopology(successResult.data);
    });

    $scope.isNodeTemplateCollapsed = false;
    $scope.isPropertiesCollapsed = false;
    $scope.isRelationshipsCollapsed = false;
    $scope.isRelationshipCollapsed = false;
    $scope.isArtifactsCollapsed = false;
    $scope.isArtifactCollapsed = false;
    $scope.isRequirementsCollapsed = false;
    $scope.isCapabilitiesCollapsed = false;

    $scope.checkMapSize = function(map) {
      return angular.isDefined(map) && map !== null && Object.keys(map).length > 0;
    };

    // do effectively add the relationship
    var doAddRelationship = function(openedOnElementName, relationshipResult, requirementName, requirementType) {
      var addRelationshipNodeTemplate = $scope.topology.topology.nodeTemplates[openedOnElementName];
      if (!addRelationshipNodeTemplate.relationships) {
        addRelationshipNodeTemplate.relationships = {};
      }

      var relationshipTemplate = {
        type: relationshipResult.relationship.elementId,
        target: relationshipResult.target,
        requirementName: requirementName,
        requirementType: requirementType,
        targetedCapabilityName: relationshipResult.targetedCapabilityName
      };
      var addRelationshipRequest = {
        relationshipTemplate: relationshipTemplate,
        archiveName: relationshipResult.relationship.archiveName,
        archiveVersion: relationshipResult.relationship.archiveVersion
      };
      topologyServices.relationshipDAO.add({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: openedOnElementName,
        relationshipName: relationshipResult.name
      }, angular.toJson(addRelationshipRequest), function(result) {
        // for refreshing the ui
        refreshTopology(result.data, openedOnElementName);
      });
      $scope.setDisplay('displayDetailsComponent', true);
    };

    $scope.openSearchRelationshipModal = function(openedOnElementName, openedOnElement, requirementName, requirement, targetNodeTemplateName) {
      if (!openedOnElement.requirementsMap[requirementName].value.canAddRel.yes) {
        console.debug('Should be able to add relationships');
        return;
      }

      $scope.sourceElement = openedOnElement;
      $scope.sourceElementName = openedOnElementName;
      $scope.requirementName = requirementName;
      $scope.requirement = requirement;
      $scope.targetNodeTemplateName = targetNodeTemplateName;

      var modalInstance = $modal.open({
        templateUrl: 'views/fragments/search/search_relationship_modal.html',
        controller: 'SearchRelationshipCtrl',
        windowClass: 'searchModal',
        scope: $scope
      });

      modalInstance.result.then(function(relationshipResult) {
        doAddRelationship(openedOnElementName, relationshipResult, requirementName, requirement.type);
      });
    };

    /**
     * NODE TEMPLATE ADD / DELETE SHOW
     */
    var nodeTemplateNameExists = function(toCheck) {
      for (var name in $scope.topology.topology.nodeTemplates) {
        if (name === toCheck) {
          return true;
        }
      }
      return false;
    };

    var nodeTemplateNameFromType = function(type) {
      var baseName = toscaService.simpleName(type);

      var i = 1;
      var tempName = baseName;
      while (nodeTemplateNameExists(tempName)) {
        i++;
        tempName = baseName + '_' + i;
      }

      return tempName;
    };

    $scope.nodeTypeSelected = function(nodeType, hostNodeName) {
      var nodeTemplName = nodeTemplateNameFromType(nodeType.elementId);
      doAddNodeTemplate(nodeTemplName, nodeType, hostNodeName);
    };

    $scope.onComponentDragged = function(e) {
      var nodeType = angular.fromJson(e.source);
      var evt = e.event;
      var hostNodeName = null;
      if (evt.target.hasAttribute('node-template-id')) {
        hostNodeName = evt.target.getAttribute('node-template-id');
      }
      $scope.nodeTypeSelected(nodeType, hostNodeName);
    };

    // refresh a node name in relationships
    function refreshNodeNameInRelationships(oldNodeName, newName) {
      for (var nodeName in $scope.topology.topology.nodeTemplates) {
        var node = $scope.topology.topology.nodeTemplates[nodeName];
        var relationships = node.relationships;
        if (UTILS.isDefinedAndNotNull(relationships)) {
          for (var relationshipId in relationships) {

            var rel = relationships[relationshipId];
            var oldRelationshipId = toscaService.generateRelationshipName(rel.type, oldNodeName);
            if (relationshipId === oldRelationshipId) {
              var newRelationshipId = toscaService.generateRelationshipName(rel.type, newName);
              delete relationships[relationshipId];
              relationships[newRelationshipId] = rel;
            }

            if (rel.target === oldNodeName) {
              rel.target = newName;
            }
          }
        }
      }
    }

    function autoOpenRelationshipModal(sourceNodeTemplateName, targetNodeTemplateName) {
      var sourceNodeTemplate = $scope.topology.topology.nodeTemplates[sourceNodeTemplateName];
      var targetNodeTemplate = $scope.topology.topology.nodeTemplates[targetNodeTemplateName];
      if (UTILS.isDefinedAndNotNull(sourceNodeTemplate) && UTILS.isDefinedAndNotNull(targetNodeTemplate)) {
        // let's try to find the requirement / for now we just support hosted on but we should improve that...
        var requirementName = nodeTemplateService.getContainerRequirement(sourceNodeTemplate, $scope.topology.nodeTypes, $scope.topology.relationshipTypes, $scope.topology.capabilityTypes);
        $scope.openSearchRelationshipModal(sourceNodeTemplateName, sourceNodeTemplate, requirementName, sourceNodeTemplate.requirementsMap[requirementName].value, targetNodeTemplateName);
      }
    }

    // do effectively add the node template
    function doAddNodeTemplate(nodeTemplateName, selectedNodeType, targetNodeTemplateName) {
      var nodeTemplateRequest = {
        'name': nodeTemplateName,
        'indexedNodeTypeId': selectedNodeType.id
      };
      topologyServices.nodeTemplate.add({
        topologyId: $scope.topology.topology.id
      }, angular.toJson(nodeTemplateRequest), function(result) {
        // for refreshing the ui
        refreshTopology(result.data, nodeTemplateName);
        if (targetNodeTemplateName) {
          autoOpenRelationshipModal(nodeTemplateName, targetNodeTemplateName);
        }
      });
    }

    $scope.nodeNameObj = {}; // added to support <tabset> "childscope issue"
    function fillNodeSelectionVars(nodeTemplate) {
      $scope.selectedNodeTemplate = nodeTemplate;
      $scope.selectedProperties = nodeTemplate.properties;
      $scope.nodeNameObj.val = nodeTemplate.name;
      $scope.selectionabstract = $scope.topology.nodeTypes[nodeTemplate.type].abstract;
    }

    $scope.selectNodeTemplate = function(newSelectedName, oldSelectedName) {
      // select the "Properties" <TAB> to see selected node details
      // selectTab('nodetemplate-details');
      document.getElementById('nodetemplate-details').click();

      $timeout(function() {
        if (oldSelectedName) {
          var oldSelected = $scope.topology.topology.nodeTemplates[oldSelectedName];
          if (oldSelected) {
            oldSelected.selected = false;
          }
        }

        var newSelected = $scope.topology.topology.nodeTemplates[newSelectedName];
        newSelected.selected = true;

        fillNodeSelectionVars(newSelected);
        $scope.setDisplay('displayDetailsComponent', true);
        $scope.$apply();
      });
    };

    $scope.deleteNodeTemplate = function(nodeTemplName) {
      topologyServices.nodeTemplate.remove({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: nodeTemplName
      }, function(result) {
        // for refreshing the ui
        refreshTopology(result.data);
        selectTab('topology-components-search');
      });
    };

    function selectTab(tabId) {
      // $timeout(callback) will wait until the current digest cycle (if any) is done, then execute your code, then run at the end a full $apply
      // without $timeout => ALREADY IN DIGEST CYCLE error
      $timeout(function() {
        document.getElementById(tabId).click();
      });
    }

    /**
     * UPDATE NODE TEMPLATE
     */

    /* Update node template name */
    $scope.updateNodeName = function(newName) {
      // Update only when the name has changed
      $scope.nodeTempNameEditError = null;
      if ($scope.selectedNodeTemplate.name !== newName) {
        var nodeNameRegEx = new RegExp(regexPatternn, 'g');
        var valid = nodeNameRegEx.test(newName);
        //verify the name format
        if (!valid) {
          $scope.nodeTempNameEditError = {
            code: 'APPLICATIONS.TOPOLOGY.NODETEMPLATE_NAME_ERROR'
          };
          return ' ';
        }

        topologyServices.nodeTemplate.updateName({
          topologyId: $scope.topology.topology.id,
          nodeTemplateName: $scope.selectedNodeTemplate.name,
          newName: newName
        }, function(resultData) {
          if (resultData.error === null) {
            var oldNodeName = $scope.selectedNodeTemplate.name;
            var nodeTemplate = $scope.topology.topology.nodeTemplates[oldNodeName];
            delete $scope.topology.topology.nodeTemplates[$scope.selectedNodeTemplate.name];
            $scope.selectedNodeTemplate.name = newName;
            $scope.topology.topology.nodeTemplates[newName] = nodeTemplate;
            refreshNodeNameInRelationships(oldNodeName, newName);
            refreshOutputMaps($scope.topology.topology, oldNodeName, newName);
          }
        }, function() {
          $scope.nodeNameObj.val = $scope.selectedNodeTemplate.name;
        });
      } // if end
      $scope.setDisplay('displayDetailsComponent', true);
    };

    /* Update properties of a node template */
    $scope.updateProperty = function(propertyDefinition, propertyValue) {
      var propertyName = propertyDefinition.name;
      var currentPropertyValue = $scope.selectedNodeTemplate.propertiesMap[propertyName].value;
      if (UTILS.isDefinedAndNotNull(currentPropertyValue)) {
        if (propertyValue === currentPropertyValue.value) {
          return;
        }
      }
      var updatePropsObject = {
        'propertyName': propertyName,
        'propertyValue': propertyValue
      };

      var updatedNodeTemplate = $scope.selectedNodeTemplate;
      return topologyServices.nodeTemplate.updateProperty({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: $scope.selectedNodeTemplate.name
      }, angular.toJson(updatePropsObject), function() {
        // update the selectedNodeTemplate properties locally
        updatedNodeTemplate.propertiesMap[propertyName].value = propertyValue;
      }).$promise;
    };

    // Add property / artifact / attributes to input list
    var initOutputMap = function(topology, nodeTemplateName, key) {
      if (angular.isDefined(topology[key])) {
        if (!angular.isDefined(topology[key][nodeTemplateName])) {
          topology[key][nodeTemplateName] = [];
        }
      } else {
        topology[key] = {};
        topology[key][nodeTemplateName] = [];
      }
    };

    var initOutputMaps = function(topology, nodeTemplateName) {
      outputKeys.forEach(function(key) {
        initOutputMap(topology, nodeTemplateName, key);
      });
    };

    var refreshOutputsMap = function(topology, oldName, newName, key) {
      var map = topology[key][oldName];
      delete topology[key][oldName];
      topology[key][newName] = map;
    };

    var refreshOutputMaps = function(topology, oldName, newName) {
      outputKeys.forEach(function(key) {
        refreshOutputsMap(topology, oldName, newName, key);
      });
    };

    var initOutputs = function(topology) {
      if (topology.nodeTemplates) {
        Object.keys(topology.nodeTemplates).forEach(function(nodeTemplateName) {
          initOutputMaps(topology, nodeTemplateName);
        });
      }
    };

    var toggleOutput = function(propertyName, outputType) {

      var nodeTemplateName = $scope.selectedNodeTemplate.name;
      var topology = $scope.topology.topology;
      var params = {
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: nodeTemplateName
      };

      if (outputType === 'outputProperties') {
        params.propertyName = propertyName;
      }
      if (outputType === 'outputAttributes') {
        params.attributeName = propertyName;
      }

      var inputIndex = topology[outputType][nodeTemplateName].indexOf(propertyName);

      if (inputIndex < 0) {
        // add input property
        topologyServices.nodeTemplate[outputType].add(
          params,
          function(successResult) {
            if (!successResult.error) {
              topology[outputType][nodeTemplateName].push(propertyName);
            } else {
              console.debug(successResult.error);
            }
          },
          function(errorResult) {
            console.debug(errorResult);
          }
        );
      } else {
        // remove input
        topologyServices.nodeTemplate[outputType].remove(
          params,
          function(successResult) {
            if (!successResult.error) {
              topology[outputType][nodeTemplateName].splice(inputIndex, 1);
            } else {
              console.debug(successResult.error);
            }
          },
          function(errorResult) {
            console.debug(errorResult);
          }
        );
      }
    };

    var createInput = function(inputId, propertyDefinition, callback) {
      topologyServices.inputs.add({
        topologyId: $scope.topology.topology.id,
        inputId: inputId
      }, angular.toJson(propertyDefinition), function() {
        // Success
        if (UTILS.isUndefinedOrNull($scope.topology.topology.inputs)) {
          $scope.topology.topology.inputs = {};
        }
        $scope.topology.topology.inputs[inputId] = UTILS.deepCopy(propertyDefinition);
        $scope.topology.topology.inputs[inputId].inputId = inputId;
        callback();
      });
    };

    var generateInputIdFromPropertyName = function(propertyName) {
      if (UTILS.isUndefinedOrNull($scope.topology.topology.inputs)) {
        return propertyName;
      }
      var i = 0;
      var inputId = propertyName;
      while ($scope.topology.topology.inputs.hasOwnProperty(inputId)) {
        inputId = propertyName + '_' + i;
        i++;
      }
      return inputId;
    };

    $scope.createInputFromRelationshipProperty = function(relationshipName, propertyName) {
      var selectedRelationshipType = $scope.topology.relationshipTypes[$scope.selectedNodeTemplate.relationshipsMap[relationshipName].value.type];
      var selectedRelationshipPropertyDefinition = selectedRelationshipType.propertiesMap[propertyName].value;
      var inputId = generateInputIdFromPropertyName(propertyName);
      createInput(inputId, selectedRelationshipPropertyDefinition, function() {
        $scope.currentInputCandidatesForRelationshipProperty.push(inputId);
        $scope.toggleRelationshipPropertyInput(relationshipName, propertyName, inputId);
      });
    };

    $scope.createInputFromProperty = function(propertyName) {
      var selectedNodeTemplateType = $scope.topology.nodeTypes[$scope.selectedNodeTemplate.type];
      var selectedPropertyDefinition = selectedNodeTemplateType.propertiesMap[propertyName].value;
      var inputId = generateInputIdFromPropertyName(propertyName);
      createInput(inputId, selectedPropertyDefinition, function() {
        $scope.currentInputCandidatesForProperty.push(inputId);
        $scope.togglePropertyInput(propertyName, inputId);
      });
    };

    $scope.getInputCandidatesForProperty = function(propertyName) {
      $scope.currentInputCandidatesForProperty = [];
      topologyServices.nodeTemplate.getInputCandidates.getCandidates({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: $scope.selectedNodeTemplate.name,
        propertyId: propertyName
      }, function(success) {
        $scope.currentInputCandidatesForProperty = success.data;
      });
    };

    $scope.getInputCandidatesForRelationshipProperty = function(relationshipName, propertyName) {
      $scope.currentInputCandidatesForRelationshipProperty = [];
      topologyServices.nodeTemplate.relationship.getInputCandidates.getCandidates({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: $scope.selectedNodeTemplate.name,
        propertyId: propertyName,
        relationshipId: relationshipName
      }, function(success) {
        $scope.currentInputCandidatesForRelationshipProperty = success.data;
      });
    };

    var updatePropertyInputIdChange = function(propertyName, inputId, properties, propertiesMap) {
      var oldPropertyEntry = propertiesMap[propertyName];
      var oldPropertyEntryIndex = properties.indexOf(oldPropertyEntry);
      propertiesMap[propertyName] = {
        key: propertyName,
        value: {
          'function': 'get_input',
          'parameters': [inputId],
          'definition': false
        }
      };
      properties[oldPropertyEntryIndex] = propertiesMap[propertyName];
    };

    $scope.togglePropertyInput = function(propertyName, inputId) {
      if (!$scope.isPropertyAssociatedToInput(propertyName, inputId)) {
        topologyServices.nodeTemplate.setInputs.set({
          topologyId: $scope.topology.topology.id,
          inputId: inputId,
          nodeTemplateName: $scope.selectedNodeTemplate.name,
          propertyId: propertyName
        }, function() {
          updatePropertyInputIdChange(propertyName, inputId, $scope.selectedNodeTemplate.properties, $scope.selectedNodeTemplate.propertiesMap);
        });
      } else {
        topologyServices.nodeTemplate.setInputs.unset({
          topologyId: $scope.topology.topology.id,
          nodeTemplateName: $scope.selectedNodeTemplate.name,
          propertyId: propertyName
        }, function() {
          $scope.selectedNodeTemplate.propertiesMap[propertyName].value = null;
        });
      }
    };

    $scope.toggleRelationshipPropertyInput = function(relationshipName, propertyName, inputId) {
      if (!$scope.isRelationshipPropertyAssociatedToInput(relationshipName, propertyName, inputId)) {
        topologyServices.nodeTemplate.relationship.setInputs.set({
          topologyId: $scope.topology.topology.id,
          inputId: inputId,
          nodeTemplateName: $scope.selectedNodeTemplate.name,
          propertyId: propertyName,
          relationshipId: relationshipName
        }, function() {
          updatePropertyInputIdChange(propertyName, inputId, $scope.selectedNodeTemplate.relationshipsMap[relationshipName].value.properties, $scope.selectedNodeTemplate.relationshipsMap[relationshipName].value.propertiesMap);
        });
      } else {
        topologyServices.nodeTemplate.relationship.setInputs.unset({
          topologyId: $scope.topology.topology.id,
          nodeTemplateName: $scope.selectedNodeTemplate.name,
          propertyId: propertyName,
          relationshipId: relationshipName
        }, function() {
          $scope.selectedNodeTemplate.relationshipsMap[relationshipName].value.propertiesMap[propertyName].value = null;
        });
      }
    };

    var isPropertyValueIsAssociatedToInput = function(propertyValue, inputId) {
      if (UTILS.isDefinedAndNotNull(propertyValue) && UTILS.isDefinedAndNotNull(propertyValue.parameters) && propertyValue.parameters.length > 0) {
        return propertyValue.parameters[0] === inputId;
      }
      return false;
    };

    $scope.isPropertyAssociatedToInput = function(propertyName, inputId) {
      var propertyValue = $scope.selectedNodeTemplate.propertiesMap[propertyName].value;
      return isPropertyValueIsAssociatedToInput(propertyValue, inputId);
    };

    $scope.isRelationshipPropertyAssociatedToInput = function(relationshipName, propertyName, inputId) {
      var propertyValue = $scope.selectedNodeTemplate.relationshipsMap[relationshipName].value.propertiesMap[propertyName].value;
      return isPropertyValueIsAssociatedToInput(propertyValue, inputId);
    };

    $scope.removeInput = function(inputId) {
      topologyServices.inputs.remove({
        topologyId: $scope.topology.topology.id,
        inputId: inputId
      }, function(success) {
        refreshTopology(success.data, $scope.selectedNodeTemplate ? $scope.selectedNodeTemplate.name : undefined);
      });
    };

    $scope.updateInput = function(oldInput, newInput) {
      topologyServices.inputs.update({
        topologyId: $scope.topology.topology.id,
        inputId: oldInput,
        newInputId: newInput
      }, function(success) {
        refreshTopology(success.data, $scope.selectedNodeTemplate ? $scope.selectedNodeTemplate.name : undefined);
      });
    };

    $scope.toggleOutputProperty = function(propertyName) {
      toggleOutput(propertyName, 'outputProperties', 'property');
    };

    $scope.toggleOutputAttribute = function(attributeName) {
      toggleOutput(attributeName, 'outputAttributes', 'attribute');
    };

    $scope.openSimpleModal = function(modalTitle, modalContent) {
      $modal.open({
        templateUrl: 'views/fragments/simple_modal.html',
        controller: ModalInstanceCtrl,
        resolve: {
          title: function() {
            return modalTitle;
          },
          content: function() {
            return modalContent;
          }
        }
      });
    };

    var ModalInstanceCtrl = ['$scope', '$modalInstance', 'title', 'content', function($scope, $modalInstance, title, content) {
      $scope.title = title;
      $scope.content = content;

      $scope.close = function() {
        $modalInstance.dismiss('close');
      };
    }];

    $scope.updateInputArtifactList = function(artifactName) {
      var nodeTemplateName = $scope.selectedNodeTemplate.name;
      var topology = $scope.topology.topology;
      var inputIndex = topology.inputArtifacts[nodeTemplateName].indexOf(artifactName);
      var artifactInput = {
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: nodeTemplateName,
        artifactName: artifactName
      };

      if (inputIndex < 0) {
        // add input artifact
        topologyServices.nodeTemplate.artifactInput.add(
          artifactInput,
          function(successResult) {
            if (!successResult.error) {
              topology.inputArtifacts[nodeTemplateName].push(artifactName);
            }
          },
          function(errorResult) {
            console.debug(errorResult);
          }
        );
      } else {
        // remove input artifact
        topologyServices.nodeTemplate.artifactInput.remove(
          artifactInput,
          function(successResult) {
            if (!successResult.error) {
              topology.inputArtifacts[nodeTemplateName].splice(inputIndex, 1);
            } else {
              console.debug(successResult.error);
            }
          },
          function(errorResult) {
            console.debug(errorResult);
          }
        );
      }

    };

    // Upload handler
    $scope.doUpload = function(file, artifactId) {
      var uploadNodeTemplate = $scope.selectedNodeTemplate;
      var artifact = uploadNodeTemplate.artifacts[artifactId];
      if (UTILS.isUndefinedOrNull($scope.uploads)) {
        $scope.uploads = {};
      }
      $scope.uploads[artifactId] = {
        'isUploading': true,
        'type': 'info'
      };
      $upload.upload({
        url: 'rest/topologies/' + $scope.topology.topology.id + '/nodetemplates/' + uploadNodeTemplate.name + '/artifacts/' + artifactId,
        file: file
      }).progress(function(evt) {
        $scope.uploads[artifactId].uploadProgress = parseInt(100.0 * evt.loaded / evt.total);
      }).success(function(success) {
        artifact.artifactRef = success.data;
        artifact.artifactName = file.name;
        $scope.uploads[artifactId].isUploading = false;
        $scope.uploads[artifactId].type = 'success';
      }).error(function(data, status) {
        $scope.uploads[artifactId].type = 'error';
        $scope.uploads[artifactId].error = {};
        $scope.uploads[artifactId].error.code = status;
        $scope.uploads[artifactId].error.message = 'An Error has occurred on the server!';
      });
    };

    $scope.onArtifactSelected = function($files, artifactId) {
      var file = $files[0];
      $scope.doUpload(file, artifactId);
    };

    /** REPLACE A NODE TEMPLATE */
    $scope.getIcon = UTILS.getIcon;

    $scope.getPossibleReplacements = function(selectedNodeTemplate) {
      topologyServices.nodeTemplate.getPossibleReplacements({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: selectedNodeTemplate.name
      }, function(result) {
        $scope.suggestedReplacements = result.data;
      });
    };

    $scope.swapNodeTemplate = function(selectedNodeTemplate, newNodeType) {
      var newNodeTemplName = nodeTemplateNameFromType(newNodeType.elementId);
      var nodeTemplateRequest = {
        name: newNodeTemplName,
        indexedNodeTypeId: newNodeType.id
      };

      topologyServices.nodeTemplate.replace({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: selectedNodeTemplate.name
      }, angular.toJson(nodeTemplateRequest), function(result) {
        refreshTopology(result.data, newNodeTemplName);
      });
    };

    /**
     * REMOVE RELATIONSHIP
     */
    $scope.removeRelationship = function(relationshipName, selectedNodeTemplate) {
      topologyServices.relationshipDAO.remove({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: selectedNodeTemplate.name,
        relationshipName: relationshipName
      }, function(result) {
        if (result.error === null) {
          refreshTopology(result.data, selectedNodeTemplate.name);
        }
      });
    };

    /**
     * SCALING POLICIES
     */
    $scope.addScalingPolicy = function(nodeTemplateName) {
      var newScalingPolicy = {
        minInstances: 1,
        maxInstances: 1,
        initialInstances: 1
      };
      topologyServices.topologyScalingPoliciesDAO.save({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: $scope.selectedNodeTemplate.name
      }, angular.toJson(newScalingPolicy), function() {
        if (UTILS.isUndefinedOrNull($scope.topology.topology.scalingPolicies)) {
          $scope.topology.topology.scalingPolicies = {};
        }
        $scope.topology.topology.scalingPolicies[nodeTemplateName] = newScalingPolicy;
      });
    };

    $scope.updateScalingPolicy = function(policyFieldName, policyFieldValue) {
      if (policyFieldValue < 0) {
        return $translate('ERRORS.800.greaterOrEqual', {
          reference: '0'
        });
      }
      var existingPolicy = $scope.topology.topology.scalingPolicies[$scope.selectedNodeTemplate.name];
      if (policyFieldName === 'initialInstances') {
        if (policyFieldValue < existingPolicy.minInstances) {
          return $translate('ERRORS.800.greaterOrEqual', {
            reference: 'minInstances'
          });
        }
        if (policyFieldValue > existingPolicy.maxInstances) {
          return $translate('ERRORS.800.lessOrEqual', {
            reference: 'maxInstances'
          });
        }
      } else if (policyFieldName === 'minInstances') {
        if (policyFieldValue > existingPolicy.initialInstances) {
          return $translate('ERRORS.800.lessOrEqual', {
            reference: 'initialInstances'
          });
        }
        if (policyFieldValue > existingPolicy.maxInstances) {
          return $translate('ERRORS.800.lessOrEqual', {
            reference: 'maxInstances'
          });
        }
      } else if (policyFieldName === 'maxInstances') {
        if (policyFieldValue < existingPolicy.initialInstances) {
          return $translate('ERRORS.800.greaterOrEqual', {
            reference: 'initialInstances'
          });
        }
        if (policyFieldValue < existingPolicy.minInstances) {
          return $translate('ERRORS.800.greaterOrEqual', {
            reference: 'minInstances'
          });
        }
      }
      existingPolicy[policyFieldName] = policyFieldValue;
      topologyServices.topologyScalingPoliciesDAO.save({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: $scope.selectedNodeTemplate.name
      }, angular.toJson(existingPolicy), undefined);
    };

    $scope.deleteScalingPolicy = function(nodeTemplateName) {
      topologyServices.topologyScalingPoliciesDAO.remove({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: $scope.selectedNodeTemplate.name
      }, function() {
        delete $scope.topology.topology.scalingPolicies[nodeTemplateName];
      });
    };

    var fillBounds = function(topology) {
      if (!topology.nodeTemplates) {
        return;
      }
      Object.keys(topology.nodeTemplates).forEach(function(nodeTemplateName) {
        var nodeTemplate = topology.nodeTemplates[nodeTemplateName];
        toscaCardinalitiesService.fillRequirementBounds($scope.topology.nodeTypes, nodeTemplate);
        toscaCardinalitiesService.fillCapabilityBounds($scope.topology.nodeTypes, $scope.topology.topology.nodeTemplates, nodeTemplate);
      });
    };
    $scope.getShortName = toscaService.simpleName;

    var isInputPropertyValue = function(propertyValue) {
      if (UTILS.isUndefinedOrNull(propertyValue)) {
        return false;
      }
      return UTILS.isDefinedAndNotNull(propertyValue.function) && propertyValue.function === 'get_input';
    };

    /**
     * Properties scopes
     */
    $scope.isInputProperty = function(propertyName) {
      var propertyValue = $scope.selectedNodeTemplate.propertiesMap[propertyName].value;
      return isInputPropertyValue(propertyValue);
    };

    $scope.isInputRelationshipProperty = function(relationshipName, propertyName) {
      var propertyValue = $scope.selectedNodeTemplate.relationshipsMap[relationshipName].value.propertiesMap[propertyName].value;
      return isInputPropertyValue(propertyValue);
    };

    $scope.isInputArtifact = function(artifactName) {
      if (UTILS.isUndefinedOrNull($scope.topology.topology.inputArtifacts)) {
        return false;
      }
      return $scope.topology.topology.inputArtifacts[$scope.selectedNodeTemplate.name].indexOf(artifactName) >= 0;
    };

    $scope.isOutputProperty = function(propertyName) {
      if (UTILS.isUndefinedOrNull($scope.topology.topology.outputProperties)) {
        return false;
      }
      return $scope.topology.topology.outputProperties[$scope.selectedNodeTemplate.name].indexOf(propertyName) >= 0;
    };

    $scope.isOutputAttribute = function(attributeName) {
      if (UTILS.isUndefinedOrNull($scope.topology.topology.outputAttributes)) {
        return false;
      }
      return $scope.topology.topology.outputAttributes[$scope.selectedNodeTemplate.name].indexOf(attributeName) >= 0;
    };

    $scope.getFormatedProperty = function(propertyKey) {
      var formatedProperty = $scope.topology.nodeTypes[$scope.selectedNodeTemplate.type].propertiesMap[propertyKey].value;
      formatedProperty.name = propertyKey;
      return formatedProperty;
    };

    $scope.getPropertyDescription = function(propertyKey) {
      return $scope.topology.nodeTypes[$scope.selectedNodeTemplate.type].propertiesMap[propertyKey].value.description;
    };

    /**
     * Capabilities and Requirements docs
     */
    $scope.getComponent = function(nodeTemplate, type) {
      var nodeType = $scope.topology.nodeTypes[nodeTemplate.type];
      var componentId = type + ':' + nodeType.archiveVersion;

      return componentService.get({
        componentId: componentId
      }, function(successResult) {
        if (successResult.error === null) {
          if (successResult.data !== null) {
            return successResult.data.description;
          }
        }
      });
    };

    /**
     * Rename a relationship
     */
    $scope.relNameObj = {};
    /* Update relationship name */
    $scope.updateRelationshipName = function(oldName, newName) {
      // Update only when the name has changed
      if (oldName !== newName) {
        topologyServices.relationship.updateName({
          topologyId: $scope.topology.topology.id,
          nodeTemplateName: $scope.selectedNodeTemplate.name,
          relationshipName: oldName,
          newName: newName
        }, function(resultData) {
          if (resultData.error === null) {
            // update the nam e of the relationship
            var relationship = $scope.topology.topology.nodeTemplates[$scope.selectedNodeTemplate.name].relationshipsMap[oldName];
            relationship.key = newName;
            $scope.topology.topology.nodeTemplates[$scope.selectedNodeTemplate.name].relationshipsMap[newName] = relationship;
            delete $scope.topology.topology.nodeTemplates[$scope.selectedNodeTemplate.name].relationshipsMap[oldName];
            delete $scope.relNameObj[oldName];
          }
        }, function() {
          $scope.relNameObj[oldName] = oldName;
        });
      } // if end
    };

    /* Update properties of a node template */
    $scope.updateRelationshipProperty = function(propertyDefinition, propertyValue, relationshipType, relationshipName) {
      var propertyName = propertyDefinition.name;
      var updateRelationshipPropertyRequest = {
        'propertyName': propertyName,
        'propertyValue': propertyValue,
        'relationshipType': relationshipType
      };

      return topologyServices.relationship.updateProperty({
        topologyId: $scope.topology.topology.id,
        nodeTemplateName: $scope.selectedNodeTemplate.name,
        relationshipName: relationshipName
      }, angular.toJson(updateRelationshipPropertyRequest), function() {
        // update the selectedNodeTemplate properties locally
      }).$promise;
    };

    // check if compute type
    $scope.isComputeType = function(nodeTemplate) {
      if (UTILS.isUndefinedOrNull($scope.topology) || UTILS.isUndefinedOrNull(nodeTemplate)) {
        return false;
      }
      var nodeType = $scope.topology.nodeTypes[nodeTemplate.type];
      return UTILS.isFromNodeType(nodeType, COMPUTE_TYPE);
    };

    $scope.checkMapSize = function(map) {
      return angular.isDefined(map) && map !== null && Object.keys(map).length > 0;
    };
  }
])
;
