package com.shen.thumbsups.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shen.thumbsups.domain.Blog;
import com.shen.thumbsups.domain.Thumb;
import com.shen.thumbsups.domain.User;
import com.shen.thumbsups.domain.vo.BlogVO;
import com.shen.thumbsups.mapper.BlogMapper;
import com.shen.thumbsups.service.BlogService;
import com.shen.thumbsups.service.ThumbService;
import com.shen.thumbsups.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author 76453
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2025-04-18 10:03:40
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService  thumbService;

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        Blog blog = this.getById(blogId);
        User user = userService.getLoginUser(request);
        return this.getBlogVO(blog, user);
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogs, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Map<Long, Boolean> blogIdHasThumbMap = new HashMap<>();
        if (ObjUtil.isNotEmpty(loginUser)) {
            Set<Long> blogIdSet = blogs.stream().map(Blog::getId).collect(Collectors.toSet());
            // 获取点赞
            List<Thumb> thumbs = thumbService.lambdaQuery()
                    .in(Thumb::getBlogId, blogIdSet)
                    .eq(Thumb::getUserId, loginUser.getId())
                    .list();
            thumbs.forEach(thumb -> blogIdHasThumbMap.put(thumb.getBlogId(), true));
        }
        return blogs.stream().map(blog -> {
            BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
            blogVO.setHasThumb(blogIdHasThumbMap.getOrDefault(blog.getId(), false));
            return blogVO;
        }).collect(Collectors.toList());
    }

    private BlogVO getBlogVO(Blog blog, User loginUser) {
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog, blogVO);
        if (loginUser == null) {
            return blogVO;
        }

        Thumb thumb = thumbService.lambdaQuery()
                .eq(Thumb::getUserId, loginUser.getId())
                .eq(Thumb::getBlogId, blog.getId())
                .one();
        blogVO.setHasThumb(thumb != null);

        return blogVO;
    }
}




