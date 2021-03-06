package alien4cloud.rest.exception;

import java.util.List;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import alien4cloud.application.InvalidDeploymentSetupException;
import alien4cloud.component.repository.exception.RepositoryTechnicalException;
import alien4cloud.exception.AlreadyExistException;
import alien4cloud.exception.DeleteDeployedException;
import alien4cloud.exception.DeleteLastApplicationEnvironmentException;
import alien4cloud.exception.DeleteReferencedObjectException;
import alien4cloud.exception.IndexingServiceException;
import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.exception.NotFoundException;
import alien4cloud.exception.VersionConflictException;
import alien4cloud.images.exception.ImageUploadException;
import alien4cloud.paas.exception.MissingPluginException;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.exception.PaaSUndeploymentException;
import alien4cloud.rest.model.RestErrorBuilder;
import alien4cloud.rest.model.RestErrorCode;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.rest.topology.UpdateTopologyException;
import alien4cloud.security.Alien4CloudAccessDeniedHandler;
import alien4cloud.utils.version.ApplicationVersionException;
import alien4cloud.utils.version.UpdateApplicationVersionException;

import com.google.common.collect.Lists;

/**
 * All technical (runtime) exception handler goes here. It's unexpected exception and is in general back-end exception or bug in our code
 *
 * @author mkv
 */
@Slf4j
@ControllerAdvice
public class RestTechnicalExceptionHandler {

    @Resource
    private Alien4CloudAccessDeniedHandler accessDeniedHandler;

    @ExceptionHandler(InvalidDeploymentSetupException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> processInvalidDeploymentSetup(InvalidDeploymentSetupException e) {
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.INVALID_DEPLOYMENT_SETUP).message("The deployment setup is invalid.").build()).build();
    }

    @ExceptionHandler(AlreadyExistException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> processAlreadyExist(AlreadyExistException e) {
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.ALREADY_EXIST_ERROR).message("The posted object already exist.").build()).build();
    }

    @ExceptionHandler(DeleteReferencedObjectException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> processDeleteReferencedObject(DeleteReferencedObjectException e) {
        log.error("Object is still referenced and cannot be deleted", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.DELETE_REFERENCED_OBJECT_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = MissingPluginException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> missingPluginExceptionHandler(MissingPluginException e) {
        log.error("PaaS provider plugin cannot be found while used on a cloud, this should not happens.", e);
        return RestResponseBuilder
                .<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.MISSING_PLUGIN_ERROR)
                        .message("The cloud plugin cannot be found. Make sure that the plugin is installed and enabled.").build()).build();
    }

    @ExceptionHandler(value = InvalidArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> invalidArgumentErrorHandler(InvalidArgumentException e) {
        log.error("Method argument is invalid", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("Method argument is invalid " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<FieldErrorDTO[]> processValidationError(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        List<FieldErrorDTO> errors = Lists.newArrayList();
        for (FieldError fieldError : result.getFieldErrors()) {
            errors.add(new FieldErrorDTO(fieldError.getField(), fieldError.getCode()));
        }
        return RestResponseBuilder.<FieldErrorDTO[]> builder().data(errors.toArray(new FieldErrorDTO[errors.size()]))
                .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("Method argument is invalid " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = IndexingServiceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> indexingServiceErrorHandler(IndexingServiceException e) {
        log.error("Indexing service has encoutered unexpected error", e);
        return RestResponseBuilder
                .<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.INDEXING_SERVICE_ERROR)
                        .message("Indexing service has encoutered unexpected error " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = RepositoryTechnicalException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> repositryServiceErrorHandler(RepositoryTechnicalException e) {
        log.error("Repository service has encoutered unexpected error", e);
        return RestResponseBuilder
                .<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.REPOSITORY_SERVICE_ERROR)
                        .message("Repository service has encoutered unexpected error " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = UpdateTopologyException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> updateTopologyErrorHandler(UpdateTopologyException e) {
        log.error("A topology cannot be updated if it's released", e);
        return RestResponseBuilder
                .<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.UPDATE_AN_RELEASED_TOPOLOGY_ERROR)
                        .message("A topology cannot be updated if it's released " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = ImageUploadException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> imageUploadErrorHandler(ImageUploadException e) {
        log.error("Image upload error", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.IMAGE_UPLOAD_ERROR).message("Image upload error " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public RestResponse<Void> notFoundErrorHandler(NotFoundException e) {
        log.error("Something not found", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.NOT_FOUND_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = VersionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> versionConflictHandler(VersionConflictException e) {
        log.error("Version conflict", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.VERSION_CONFLICT_ERROR).message(e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = PaaSDeploymentException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> paaSDeploymentErrorHandler(PaaSDeploymentException e) {
        log.error("Error in PaaS Deployment", e);
        return RestResponseBuilder
                .<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.APPLICATION_DEPLOYMENT_ERROR).message("Application cannot be deployed " + e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = PaaSUndeploymentException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> paaSUndeploymentErrorHandler(PaaSUndeploymentException e) {
        log.error("Error in UnDeployment", e);
        return RestResponseBuilder
                .<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.APPLICATION_UNDEPLOYMENT_ERROR).message("Application cannot be undeployed " + e.getMessage())
                        .build()).build();
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    public RestResponse<Void> accessDeniedHandler(AccessDeniedException e) {
        return accessDeniedHandler.getUnauthorizedRestError();
    }

    @ExceptionHandler(value = Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> catchAllErrorHandler(Exception e) {
        log.error("Uncategorized error", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.UNCATEGORIZED_ERROR).message("Uncategorized error " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = ApplicationVersionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> applicationVersionErrorHandler(ApplicationVersionException e) {
        log.error("Application version error", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.APPLICATION_VERSION_ERROR).message("Application version error : " + e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = UpdateApplicationVersionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> updteApplicationVersionErrorHandler(UpdateApplicationVersionException e) {
        log.error("Update application version error", e);
        return RestResponseBuilder
                .<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.UPDATE_RELEASED_APPLICATION_VERSION_ERROR)
                        .message("Update application version error : " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = DeleteLastApplicationEnvironmentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> deleteLastApplicationEnvironmentErrorHandler(DeleteLastApplicationEnvironmentException e) {
        log.error("Delete last application environment error", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.APPLICATION_ENVIRONMENT_ERROR).message("Application version error : " + e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = DeleteDeployedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> deleteDeployedErrorHandler(DeleteDeployedException e) {
        log.error("Delete deployed element error", e);
        return RestResponseBuilder
                .<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.APPLICATION_ENVIRONMENT_DEPLOYED_ERROR)
                        .message("Application environment delete error : " + e.getMessage()).build()).build();
    }
}
