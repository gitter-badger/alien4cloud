'use strict';

var alien4cloudApp = angular.module('alienUiApp', ['ngCookies', 'ngResource', 'ngSanitize', 'ui.router', 'alienAuth', 'searchServices',
    'pascalprecht.translate', 'ui.bootstrap', 'angularFileUpload', 'xeditable', 'ui.ace', 'toaster', 'angular-utils-ui', 'treeControl'
  ])
  .config(function($stateProvider, $urlRouterProvider, $httpProvider, $parseProvider) {
    $parseProvider.unwrapPromises(true);
    $httpProvider.interceptors.push('restTechnicalErrorInterceptor');

    $urlRouterProvider.when('/applications', '/applications/list');
    $urlRouterProvider.when('/topologytemplates', '/topologytemplates/list');
    $urlRouterProvider.when('/components', '/components/list');
    $urlRouterProvider.otherwise('/');

    // state for restricted access
    $stateProvider.state('restricted', {
      url: '/restricted',
      templateUrl: 'views/restricted.html'
    })

    .state('home', {
      url: '/',
      templateUrl: 'views/main.html'
    })

    .state('home_user', {
      templateUrl: 'views/main.html'
    })

    .state('user_home_admin', {
      templateUrl: 'views/admin/home.html',
      controller: 'AdminHomeCtrl'
    })

    // components states
    .state('components', {
      url: '/components',
      template: '<ui-view/>'
    }).state('components.list', {
      url: '/list',
      templateUrl: 'views/component_list.html',
      controller: 'SearchComponentCtrl'
    }).state('components.detail', {
      url: '/component/:id',
      templateUrl: 'views/component_details.html',
      controller: 'ComponentDetailsCtrl'
    })

    // applications states
    .state('applications', {
      url: '/applications',
      template: '<ui-view/>'
    }).state('applications.list', {
      url: '/list',
      templateUrl: 'views/applications/application_list.html',
      controller: 'ApplicationListCtrl'
    }).state('applications.detail', {
      url: '/:id',
      resolve: {
        application: ['applicationServices', '$stateParams',
          function(applicationServices, $stateParams) {
            return applicationServices.get({
              applicationId: $stateParams.id
            }).$promise;
          }
        ],
        topologyId: ['$http', '$stateParams',
          function($http, $stateParams) {
            return $http.get('rest/applications/' + $stateParams.id + '/topology').then(function(result) {
              return result.data.data;
            });
          }
        ],
        applicationEventServices: ['applicationEventServicesFactory', '$stateParams',
          function(applicationEventServicesFactory, $stateParams) {
            return applicationEventServicesFactory($stateParams.id);
          }
        ]
      },
      templateUrl: 'views/common/vertical_menu_layout.html',
      controller: 'ApplicationCtrl'
    }).state('applications.detail.info', {
      url: '/infos',
      templateUrl: 'views/applications/application_infos.html',
      controller: 'ApplicationInfosCtrl'
    }).state('applications.detail.topology', {
      url: '/topology',
      templateUrl: 'views/topology/topology_editor.html',
      controller: 'TopologyCtrl'
    }).state('applications.detail.plans', {
      url: '/workflow',
      templateUrl: 'views/topology/plan_graph.html',
      controller: 'TopologyPlanGraphCtrl'
    }).state('applications.detail.deployment', {
      url: '/deployment',
      resolve: {
        environment: ['$http', 'application',
          function($http, application) {
            return $http.get('rest/applications/' + application.data.id + '/environments').then(function(result) {
              return result.data.data;
            });
          }
        ]
      },
      templateUrl: 'views/applications/application_deployment.html',
      controller: 'ApplicationDeploymentCtrl'
    }).state('applications.detail.runtime', {
      url: '/runtime',
      templateUrl: 'views/applications/topology_runtime.html',
      resolve: {
        environment: ['$http', 'application',
          function($http, application) {
            return $http.get('rest/applications/' + application.data.id + '/environments').then(function(result) {
              return result.data.data;
            });
          }
        ]
      },
      controller: 'TopologyRuntimeCtrl'
    }).state('applications.detail.users', {
      url: '/users',
      templateUrl: 'views/applications/application_users.html',
      resolve: {
        applicationRoles: ['$resource',
          function($resource) {
            return $resource('rest/auth/roles/application', {}, {
              method: 'GET'
            }).get().$promise;
          }
        ]
      },
      controller: 'ApplicationUsersCtrl'
    })

    // topology templates
    .state('topologytemplates', {
      url: '/topologytemplates',
      template: '<ui-view/>'
    }).state('topologytemplates.list', {
      url: '/list',
      templateUrl: 'views/topologytemplates/topology_template_list.html',
      controller: 'TopologyTemplateListCtrl'
    }).state('topologytemplates.detail', {
      url: '/:id',
      templateUrl: 'views/topologytemplates/topology_template.html',
      resolve: {
        topologyTemplate: ['topologyTemplateService', '$stateParams',
          function(topologyTemplateService, $stateParams) {
            return topologyTemplateService.get({
              topologyTemplateId: $stateParams.id
            }).$promise;
          }
        ]
      },
      controller: 'TopologyTemplateCtrl'
    }).state('topologytemplates.detail.topology', {
      url: '/topology',
      templateUrl: 'views/topology/topology_editor.html',
      resolve: {
        topologyId: ['topologyTemplate',
          function(topologyTemplate) {
            return topologyTemplate.data.topologyId;
          }
        ]
      },
      controller: 'TopologyCtrl'
    })

    // cloud service archives
    .state('csars', {
      url: '/csars',
      template: '<ui-view/>'
    }).state('csars.list', {
      url: '/list',
      templateUrl: 'views/csar_list.html',
      controller: 'CsarListCtrl'
    }).state('csardetail', {
      url: '/csar/:csarId',
      templateUrl: 'views/csar_details.html',
      controller: 'CsarDetailsCtrl'
    }).state('csardetailnode', {
      url: '/csar/:csarId/node/:nodeTypeId',
      templateUrl: 'views/csar_component_details.html',
      controller: 'CsarComponentDetailsCtrl'
    })

    // administration
    .state('admin', {
      url: '/admin',
      templateUrl: 'views/common/vertical_menu_layout.html',
      controller: 'AdminCtrl'
    }).state('admin.home', {
      url: '/',
      templateUrl: 'views/common/vertical_menu_homepage_layout.html',
      controller: 'AdminLayoutHomeCtrl'
    }).state('admin.users', {
      url: '/users',
      templateUrl: 'views/users/user_list.html',
      controller: 'UsersCtrl'
    }).state('admin.plugins', {
      url: '/plugins',
      templateUrl: 'views/plugin_list.html',
      controller: 'PluginCtrl'
    }).state('admin.metaprops', {
      url: '/metaproperties',
      template: '<ui-view/>'
    }).state('admin.metaprops.list', {
      url: '/list',
      templateUrl: 'views/meta-props/tag_configuration_list.html',
      controller: 'TagConfigurationListCtrl'
    }).state('admin.metaprops.detail', {
      url: '/:id',
      templateUrl: 'views/meta-props/tag_configuration.html',
      controller: 'TagConfigurationCtrl'
    }).state('admin.clouds', {
      url: '/clouds',
      template: '<ui-view/>'
    }).state('admin.clouds.list', {
      url: '/list',
      templateUrl: 'views/clouds/cloud_list.html',
      controller: 'CloudListController'
    }).state('admin.clouds.detail', {
      url: '/:id',
      templateUrl: 'views/clouds/cloud_detail.html',
      controller: 'CloudDetailController'
    }).state('admin.metrics', {
      url: '/metrics',
      templateUrl: 'views/admin/metrics.html',
      controller: 'MetricsController'
    }).state('admin.cloud-images', {
      url: '/cloud-images',
      template: '<ui-view/>'
    }).state('admin.cloud-images.list', {
      url: '/list',
      templateUrl: 'views/cloud-images/cloud_image_list.html',
      controller: 'CloudImageListController'
    }).state('admin.cloud-images.detail', {
      url: '/:id',
      resolve: {
        cloudImage: ['cloudImageServices', '$stateParams',
          function(cloudImageServices, $stateParams) {
            return cloudImageServices.get({
              id: $stateParams.id
            }).$promise.then(function(success) {
                return success.data;
              });
          }
        ]
      },
      templateUrl: 'views/cloud-images/cloud_image_detail.html',
      controller: 'CloudImageDetailController'
    });
  });

/* i18n : angular-translate configuration */
alien4cloudApp.config(['$translateProvider',
  function($translateProvider) {
    $translateProvider.translations({
      CODE: 'fr-fr'
    });
    // Default language to load
    $translateProvider.preferredLanguage('fr-fr');

    // Static file loader
    $translateProvider.useStaticFilesLoader({
      prefix: 'data/languages/locale-',
      suffix: '.json'
    });
  }
]);

alien4cloudApp.run(['alienNavBarService', 'editableOptions', 'editableThemes', 'quickSearchServices', '$rootScope', 'alienAuthService', '$state',
  function(alienNavBarService, editableOptions, editableThemes, quickSearchServices, $rootScope, alienAuthService, $state) {
    alienNavBarService.menu.navbrandimg = 'images/cloudalien-small.png';

    // check when the state is about to change
    $rootScope.$on('$stateChangeStart', function(event, toState) {
      alienAuthService.getStatus().$promise.then(function(result) {
        if(toState.name.indexOf('home') === 0 && alienAuthService.hasRole('ADMIN')) {
          $state.go('user_home_admin');
        }
        // check all the menu array & permissions
        alienNavBarService.menu.left.forEach(function(menuItem) {
          var menuType = menuItem.id.split('.')[1];
          var foundMenuIndex = toState.name.indexOf(menuType);
          if (foundMenuIndex === 0 && menuItem.hasRole === false) {
            $state.go('restricted');
          }
        });
      });
    });

    alienNavBarService.menu.left = [{
      'roles': [],
      'id': 'menu.applications',
      'key': 'NAVBAR.MENU_APPS',
      'state': 'applications',
      'icon': 'fa fa-desktop'
    }, {
      'roles': ['ARCHITECT'],
      'id': 'menu.topologytemplates',
      'key': 'NAVBAR.MENU_TOPOLOGY_TEMPLATE',
      'state': 'topologytemplates.list',
      'icon': 'fa fa-sitemap'
    }, {
      'roles': ['COMPONENTS_MANAGER', 'COMPONENTS_BROWSER'],
      'id': 'menu.components',
      'key': 'NAVBAR.MENU_COMPONENTS',
      'state': 'components.list',
      'icon': 'fa fa-cubes'
    }, {
      'roles': ['COMPONENTS_MANAGER'],
      'id': 'menu.csars',
      'key': 'NAVBAR.MENU_CSARS',
      'state': 'csars.list',
      'icon': 'fa fa-archive'
    }, {
      'roles': ['ADMIN'],
      'id': 'menu.admin',
      'key': 'NAVBAR.MENU_ADMIN',
      'state': 'admin.home',
      'icon': 'fa fa-wrench'
    }];

    alienNavBarService.quickSearchHandler = {
      'doQuickSearch': quickSearchServices.doQuickSearch,
      'onItemSelected': quickSearchServices.onItemSelected,
      'waitBeforeRequest': 500,
      'minLength': 3
    };

    /* angular-xeditable config */
    editableThemes.bs3.inputClass = 'input-sm';
    editableThemes.bs3.buttonsClass = 'btn-sm';
    editableOptions.theme = 'bs3';
  }
]);