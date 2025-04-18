package com.shen.thumbsups.service;

import com.shen.thumbsups.domain.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shen.thumbsups.domain.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author 76453
* @description 针对表【blog】的数据库操作Service
* @createDate 2025-04-18 10:03:40
*/
public interface BlogService extends IService<Blog> {


    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    List<BlogVO> getBlogVOList(List<Blog> blogs, HttpServletRequest request);

}
