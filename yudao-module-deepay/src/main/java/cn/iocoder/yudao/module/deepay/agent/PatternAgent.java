package cn.iocoder.yudao.module.deepay.agent;

/**
 * PatternAgent — 生成打版文件路径。
 */
public class PatternAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        ctx.patternFile = "/pattern/test.dxf";
        return ctx;
    }

}

