package com.shen.thumbsups.mapper;

import com.shen.thumbsups.domain.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
* @author 76453
* @description 针对表【blog】的数据库操作Mapper
* @createDate 2025-04-18 10:03:40
* @Entity generator.domain.Blog
*/
public interface BlogMapper extends BaseMapper<Blog> {

    void batchUpdateThumbsCount(@Param("countMap")Map<Long, Long> countMap);

}




