package com.example.uploadtus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


@SpringBootApplication
public class UploadTusApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadTusApplication.class, args);
    }
}

@Configuration
@EnableWebMvc
class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("/resources/static/");
    }
}

/**
 * TUS HEADER所需环境变量
 */
class TusConstant {

    public static final String TUS_RESUMABLE_HEADER = "Tus-Resumable";
    public static final String TUS_RESUMABLE_VALUE = "1.0.0";

    public static final String TUS_VERSION_HEADER = "Tus-Version";
    public static final String TUS_VERSION_VALUE = "1.0.0,0.2.2,0.2.1";

    public static final String TUS_EXTENTION_HEADER = "Tus-Extension";
    public static final String TUS_EXTENTION_VALUE = "creation,expiration";

    public static final String TUS_MAX_SIZE_HEADER = "Tus-Max-Size";

    public static final String UPLOAD_OFFSET_HEADER = "Upload-Offset";

    public static final String UPLOAD_LENGTH_HEADER = "Upload-Length";

    public static final String LOCATION_HEADER = "Location";

    public static final String ACCESS_CONTROL_ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN_VALUE = "*";

    public static final String ACCESS_CONTROL_ALLOW_METHIDS_HEADER = "Access-Control-Allow-Methods";
    public static final String ACCESS_CONTROL_ALLOW_METHIDS_VALUE = "GET,PUT,POST,DELETE";

    public static final String ACCESS_CONTROL_EXPOSE_HEADER = "Access-Control-Expose-Headers";
    public static final String ACCESS_CONTROL_EXPOSE_OPTIONS_VALUE = "Tus-Resumable, Tus-Version, Tus-Max-Size, Tus-Extension";
    public static final String ACCESS_CONTROL_EXPOSE_POST_VALUE = "Location, Tus-Resumable";
    public static final String ACCESS_CONTROL_EXPOSE_HEAD_VALUE = "Upload-Offset, Upload-Length, Tus-Resumable";
    public static final String ACCESS_CONTROL_EXPOSE_PATCH_VALUE = "Upload-Offset, Tus-Resumable";

    public static final String URL_PREFIX = "tus/";
}

/**
 * 错误信息
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
class BaseException extends RuntimeException {
    protected Integer code;
    protected String message;

    public static final BaseException UNKNOWN_ERROR = new BaseException(1000, "未知错误");
    public static final BaseException FILE_PREVIEW_FAIL = new BaseException(
            1001, "不支持的文件类型!");
    public static final BaseException FILE_NOT_FOUND = new BaseException(
            1002, "文件不存在!");
    public static final BaseException FILE_READ_IO_FAIL = new BaseException(
            1003, "读取过程中出现错误!");
    public static final BaseException FILE_UPLOAD_UNCOMPLETED = new BaseException(
            1004, "文件未上传完成!");
    public static final BaseException FILE_BEYOUND_SIZE = new BaseException(
            1005, "文件尺寸大于原始尺寸!");
    public static final BaseException FILE_UPLOAD_OFFSET_ERROR = new BaseException(
            1007, "Upload Offsets 不一致!");
    public static final BaseException HEADER_CONTENT_TYPE_NEED = new BaseException(
            1008, "需要 Content-Type Header!");
    public static final BaseException HEADER_OFFSET_NEED = new BaseException(
            1009, "需要 Offset Header Header!");
    public static final BaseException HEADER_CONTENT_LENGTH_NEED = new BaseException(
            5010, "需要 Content-Length Header!");
    public static final BaseException FILE_SIZE_ERROR = new BaseException(
            5011, "文件长度错误!");

}


/**
 * Tus所需的Contriller
 * 常量中的 URL_PREFIX 必须和当前 Controller 的 RequestMapping 映射一致
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/tus")
class UploadController{
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${tus.max-size}")
    private Long tusMaxSize;

    @Autowired
    private UploadFileRepository uploadFileRepository;

    @Autowired
    private UploadFileService uploadFileService;

    /**
     * 获取Tus支持的信息
     * @param response
     * @return
     */
    @RequestMapping(method = RequestMethod.OPTIONS)
    public RequestEntity<?> optionRequest(HttpServletResponse response){
        logger.debug("1 - OPTIONS:");
        logger.debug("OPTIONS END");

        response.setHeader(TusConstant.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, TusConstant.ACCESS_CONTROL_ALLOW_ORIGIN_VALUE);
        response.setHeader(TusConstant.ACCESS_CONTROL_EXPOSE_HEADER, TusConstant.ACCESS_CONTROL_EXPOSE_OPTIONS_VALUE);
        response.setHeader(TusConstant.TUS_RESUMABLE_HEADER, TusConstant.TUS_RESUMABLE_VALUE);
        response.setHeader(TusConstant.TUS_VERSION_HEADER, TusConstant.TUS_VERSION_VALUE);
        response.setHeader(TusConstant.TUS_MAX_SIZE_HEADER, tusMaxSize.toString());
        response.setHeader(TusConstant.TUS_EXTENTION_HEADER, TusConstant.TUS_EXTENTION_VALUE);
        response.setHeader(TusConstant.ACCESS_CONTROL_ALLOW_METHIDS_HEADER, TusConstant.ACCESS_CONTROL_ALLOW_METHIDS_VALUE);
        return null;
    }

    /**
     * 获取Tus支持的信息
     * @param response
     * @return
     */
    @RequestMapping(method = RequestMethod.OPTIONS, value = "/{guid}")
    public RequestEntity<?> optionRequest(@PathVariable String guid, HttpServletResponse response){
        logger.debug("2 - OPTIONS: {guid}", guid);
        UploadFile uploadFile = this.uploadFileRepository.getByGuid(guid);
        if(uploadFile == null){
            throw BaseException.FILE_NOT_FOUND;
        }
        logger.debug("file offset: " + uploadFile.getOffset());
        logger.debug("OPTIONS END");

        response.setHeader(TusConstant.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, TusConstant.ACCESS_CONTROL_ALLOW_ORIGIN_VALUE);
        response.setHeader(TusConstant.ACCESS_CONTROL_EXPOSE_HEADER, TusConstant.ACCESS_CONTROL_EXPOSE_OPTIONS_VALUE);
        response.setHeader(TusConstant.TUS_RESUMABLE_HEADER, TusConstant.TUS_RESUMABLE_VALUE);
        response.setHeader(TusConstant.TUS_VERSION_HEADER, TusConstant.TUS_VERSION_VALUE);
        response.setHeader(TusConstant.TUS_MAX_SIZE_HEADER, tusMaxSize.toString());
        response.setHeader(TusConstant.TUS_EXTENTION_HEADER, TusConstant.TUS_EXTENTION_VALUE);
        response.setHeader(TusConstant.ACCESS_CONTROL_ALLOW_METHIDS_HEADER, TusConstant.ACCESS_CONTROL_ALLOW_METHIDS_VALUE);
        response.setHeader(TusConstant.UPLOAD_OFFSET_HEADER, Integer.toString(uploadFile.getOffset()));
        response.setStatus(204);
        return null;
    }

    /**
     * 创建新的文件
     * @param uploadLength
     * @param uriComponentsBuilder
     * @param response
     * @return
     */
    @RequestMapping(method = RequestMethod.POST)
    public RequestEntity<?> postRequest(@RequestHeader("Upload-Length") Integer uploadLength,
                                 UriComponentsBuilder uriComponentsBuilder,
                                 HttpServletResponse response){
        logger.debug("3 - POST START");
        logger.debug("Final-Length header value: " + Long.toString(uploadLength));

        if(uploadLength < 1){
            throw BaseException.FILE_SIZE_ERROR;
        }

        if(uploadLength > tusMaxSize){
            throw BaseException.FILE_BEYOUND_SIZE;
        }

        UploadFile uploadFile = new UploadFile();
        uploadFile.setUploadSize(uploadLength);
        uploadFile.setOffset(0);
        uploadFile.setCompleted(false);

        this.uploadFileRepository.save(uploadFile);

        String location = uriComponentsBuilder.path("/" + TusConstant.URL_PREFIX + uploadFile.getGuid()).build().toString();

        response.setHeader(TusConstant.ACCESS_CONTROL_EXPOSE_HEADER, TusConstant.ACCESS_CONTROL_EXPOSE_POST_VALUE);
        response.setHeader(TusConstant.LOCATION_HEADER, location);
        response.setHeader(TusConstant.TUS_RESUMABLE_HEADER, TusConstant.TUS_RESUMABLE_VALUE);
        response.setStatus(201);

        logger.debug(location);
        logger.debug("POST END");
        return null;
    }

    /**
     * 查询文件信息
     * @param guid
     * @param response
     * @return
     */
    @RequestMapping(method = RequestMethod.HEAD, value = "/{guid}")
    public ResponseEntity<?> headRequest(@PathVariable String guid, HttpServletResponse response){
        logger.debug("4 HEAD START");
        logger.debug("guid value: " + guid);

        UploadFile uploadFile = this.uploadFileRepository.getByGuid(guid);
        if(uploadFile == null){
            throw BaseException.FILE_NOT_FOUND;
        }

        logger.debug("file offset: " + uploadFile.getOffset());
        logger.debug("HEAD END");

        response.setHeader(TusConstant.ACCESS_CONTROL_EXPOSE_HEADER, TusConstant.ACCESS_CONTROL_EXPOSE_HEAD_VALUE);
        response.setHeader(TusConstant.UPLOAD_OFFSET_HEADER, Integer.toString(uploadFile.getOffset()));
        response.setHeader(TusConstant.UPLOAD_LENGTH_HEADER, Integer.toString(uploadFile.getUploadSize()));
        response.setHeader(TusConstant.TUS_RESUMABLE_HEADER, TusConstant.TUS_RESUMABLE_VALUE);
        response.setStatus(200);
        return null;
    }

    /**
     * 上传文件
     * @param uploadOffset
     * @param contentLength
     * @param contentType
     * @param guid
     * @param inputStream
     * @param response
     * @return
     * @throws Exception
     */
    @RequestMapping(method = RequestMethod.PATCH, value = "/{guid}")
    public RequestEntity<?> patchRequest(@RequestHeader("Upload-Offset") Integer uploadOffset,
                                  @RequestHeader("Content-Length") Integer contentLength,
                                  @RequestHeader("Content-Type") String contentType,
                                  @PathVariable String guid,
                                  InputStream inputStream,
                                  HttpServletResponse response) throws Exception{
        logger.debug("5 - PATCH START");
        logger.debug("uuid value: " + guid);
        logger.debug("Upload-Offset: " + uploadOffset);
        logger.debug("Content-Length: " + contentLength);
        logger.debug("Content-Type: " + contentType);

        if(uploadOffset == null || uploadOffset < 0){
            throw BaseException.HEADER_OFFSET_NEED;
        }

        if(contentLength == null || contentLength < 0){
            throw BaseException.HEADER_CONTENT_LENGTH_NEED;
        }

        if(!contentType.equals("application/offset+octet-stream")){
            throw BaseException.HEADER_CONTENT_TYPE_NEED;
        }

        UploadFile uploadFile = this.uploadFileRepository.getByGuid(guid);
        if(uploadFile == null){
            throw BaseException.FILE_NOT_FOUND;
        }

        logger.debug("Offset: " + uploadFile.getOffset());
        logger.debug("FinalLength: " + uploadFile.getUploadSize());

        if(!Objects.equals(uploadOffset, uploadFile.getOffset())){
            throw BaseException.FILE_UPLOAD_OFFSET_ERROR;
        }

        if(uploadFile.getUploadSize() < uploadFile.getOffset()){
            throw BaseException.FILE_SIZE_ERROR;
        }

        if(Objects.equals(uploadFile.getUploadSize(), uploadFile.getOffset())){
            logger.debug("Upload-length == Offset");
            logger.debug("PATCH END");
            if(!uploadFile.isCompleted()){
                uploadFile.setCompleted(true);
                this.uploadFileRepository.save(uploadFile);
            }
            response.setStatus(200);
            return null;
        }

        int newOffset = this.uploadFileService.processStream(guid, inputStream);

        if(newOffset > uploadFile.getUploadSize()){
            throw BaseException.FILE_BEYOUND_SIZE;
        }

        logger.debug("New Offset: " + Integer.toString(newOffset));

        uploadFile.setOffset(newOffset);
        this.uploadFileRepository.save(uploadFile);

        logger.debug("PATCH END");

        response.setHeader(TusConstant.ACCESS_CONTROL_EXPOSE_HEADER, TusConstant.ACCESS_CONTROL_EXPOSE_PATCH_VALUE);
        response.setHeader(TusConstant.TUS_RESUMABLE_HEADER, TusConstant.TUS_RESUMABLE_VALUE);
        response.setHeader(TusConstant.UPLOAD_OFFSET_HEADER, Integer.toString(uploadFile.getOffset()));
        response.setStatus(204);
        return null;
    }

    /**
     * 文件下载，根据 Guid
     * @return
     */
    @RequestMapping(path = "/{guid}", method = RequestMethod.GET)
    public ResponseEntity<Resource> getRequest(@PathVariable String guid) throws Exception {
        logger.debug("6 - GET START");
        logger.debug("GUID value: " + guid);

        UploadFile uploadFile = this.uploadFileRepository.getByGuid(guid);
        if(uploadFile == null){
            throw BaseException.FILE_NOT_FOUND;
        }

        if(!uploadFile.isCompleted() && Objects.equals(uploadFile.getUploadSize(), uploadFile.getOffset())){
            logger.debug("Upload-length == Offset");
            uploadFile.setCompleted(true);
            this.uploadFileRepository.save(uploadFile);
        }

        if(!uploadFile.isCompleted()){
            throw BaseException.FILE_UPLOAD_UNCOMPLETED;
        }

        Resource fileResource = uploadFileService.loadResource(guid);

        logger.debug("GET END");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + uploadFile.getFileName() + "\"").body(fileResource);
    }

    @RequestMapping(path = "/list", method = RequestMethod.GET)
    public ApiResultVO list() {
        return new ApiResultVO(0, "success", this.uploadFileRepository.getAll());
    }
}

@Service
class UploadFileServiceImpl implements UploadFileService{
    private static final Logger logger = LoggerFactory.getLogger(UploadFileServiceImpl.class);

    private final String uploadPath;

    @Autowired
    public UploadFileServiceImpl(Environment environment) {
        logger.debug("STARTING UploadFileService");
        uploadPath = environment.getProperty("tus.upload-path");
        File file = new File(uploadPath);

        if (!file.isDirectory() && !file.mkdir()){
            throw new BaseException(900, "路径错误");
        }
        if (!file.canWrite() || !file.canRead()){
            throw new BaseException(900, "路径只读，不可写" + uploadPath);
        }
        logger.debug("UploadFileService 启动成功");
    }

    @Override
    public int processStream(String uuid, InputStream inputStream) throws Exception {
        String filename = StringUtils.cleanPath(uuid);
        Path path = Paths.get(uploadPath);
        File file = new File(path.resolve(filename).toString());

        if (!file.isFile()){
            new FileOutputStream(file).close();
            if(!file.isFile()){
                logger.error("无法创建文件");
                throw new BaseException(900, "无法创建文件" + uploadPath);
            }
        }

        InputStream storageFile;
        try{
            storageFile = new FileInputStream(file);
        }catch(IOException e){
            logger.error("读取已存在文件失败");
            throw new BaseException(900, "读取已存在文件失败");
        }

        storageFile = new SequenceInputStream(storageFile, inputStream);
        Files.copy(storageFile, path.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        file = new File(path.resolve(filename).toString());
        return (int) file.length();
    }

    @Override
    public Resource loadResource(String guid) throws Exception {
        String filename = StringUtils.cleanPath(guid);

        Resource resource = new UrlResource(Paths.get(uploadPath).resolve(filename).toUri());
        if (resource.exists() || resource.isReadable()) {
            return resource;
        }
        else {
            throw new BaseException(900, filename);
        }
    }
}

@Setter
@Getter
@ToString
class UploadFile {
    private String guid;
    private String fileName;
    private Integer uploadSize;
    private Integer offset;
    private boolean isCompleted;

    public UploadFile() {
        this.guid = UUID.randomUUID().toString().replace("-",  "");
    }
}

interface UploadFileService{
    int processStream(String uuid, InputStream inputStream) throws Exception;
    Resource loadResource(String uuid) throws Exception;
}

interface UploadFileRepository {
    public String save(UploadFile uploadFile);
    public UploadFile getByGuid(String guid);
    public Map<String, UploadFile> getAll();
}

@Component
class UploadFileRepositoryImpl implements UploadFileRepository{

    private Map<String, UploadFile> fileMap = new HashMap<>();

    @Override
    public String save(UploadFile uploadFile) {
        this.fileMap.put(uploadFile.getGuid(), uploadFile);
        return uploadFile.toString();
    }

    @Override
    public UploadFile getByGuid(String guid) {
        return this.fileMap.get(guid);
    }

    @Override
    public Map<String, UploadFile> getAll() {
        return this.fileMap;
    }
}

@Getter
@Setter
@ToString
@AllArgsConstructor
class ApiResultVO{
    private Integer code;
    private String message;
    private Object data;

    public static ApiResultVO success() {
        return new ApiResultVO(0, "成功", null);
    }
}

/**
 * 全局错误处理
 */
@ControllerAdvice
@ResponseStatus(HttpStatus.OK)
class ControllerErrorHandler{

    // 日志记录
    private static final Logger logger = LoggerFactory.getLogger(ControllerErrorHandler.class);

    @ResponseBody
    @ExceptionHandler(value = BaseException.class)
    public ApiResultVO handleISSException(BaseException e) {
        logger.error(e.getMessage());
        return new ApiResultVO(e.getCode(), e.getMessage(), "");
    }

    @ResponseBody
    @ExceptionHandler(value = IOException.class)
    public ApiResultVO handleIOException(IOException e) {
        logger.error(e.getMessage());
        return new ApiResultVO(999, e.getMessage(), "");
    }
}
