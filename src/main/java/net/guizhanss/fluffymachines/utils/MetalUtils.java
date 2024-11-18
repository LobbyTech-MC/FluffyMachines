package net.guizhanss.fluffymachines.utils;

import lombok.experimental.UtilityClass;
import net.guizhanss.guizhanlib.common.utils.StringUtil;

import java.util.Locale;

@UtilityClass
public final class MetalUtils {

    public static String getMetalName(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "IRON":
                yield "铁";
            case "GOLD":
                yield "金";
            case "COPPER":
                yield "铜";
            case "TIN":
                yield "锡";
            case "SILVER":
                yield "银";
            case "LEAD":
                yield "铅";
            case "ALUMINUM":
                yield "铝";
            case "ZINC":
                yield "锌";
            case "MAGNESIUM":
                yield "镁";
            default:
                yield StringUtil.humanize(type);
        };
    }
}
