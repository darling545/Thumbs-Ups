package com.shen.thumbsups.controller;

import com.shen.thumbsups.common.BaseResponse;
import com.shen.thumbsups.common.ResultUtils;
import com.shen.thumbsups.domain.dto.thumb.DoThumbRequest;
import com.shen.thumbsups.service.ThumbService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("thumb")
public class ThumbController {

    @Resource
    private ThumbService thumbService;

    @PostMapping("/do")
    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean doThumb = thumbService.doThumb(doThumbRequest, request);
        return ResultUtils.success(doThumb);
    }


    @PostMapping("/undo")
    public BaseResponse<Boolean> undoThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean undoThumb = thumbService.undoThumb(doThumbRequest, request);
        return ResultUtils.success(undoThumb);
    }
}
