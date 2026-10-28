package mods.mewkooo.bedrockmodel.v1.resource;

import mods.mewkooo.bedrockmodel.v1.common.model.BedrockModel;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;

import java.util.function.Function;

public record BedrockModelResourceProcessor (RawResourceLoader rawLoader,
                                             Function<BedrockModelPOJO, BedrockModel> converter){
}
