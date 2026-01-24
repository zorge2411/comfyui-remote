package com.example.comfyui_remote.data

import com.google.gson.JsonObject

// We use JsonObject for now because the structure of object_info is dynamic (keys are node names)
// But we can define helper classes if we want to parse specific node details.

data class ComfyObjectInfo(
    val dynamicNodes: JsonObject // The entire response is a map of NodeName -> NodeDefinition
)

/*
 example structure of a node definition:
 "CheckpointLoaderSimple": {
     "input": {
         "required": {
             "ckpt_name": [ ["file1.ckpt", "file2.ckpt"], {"default": "file1"} ]
         }
     },
     "output": ["MODEL", "CLIP", "VAE"],
     "output_is_list": [false, false, false],
     "output_name": ["MODEL", "CLIP", "VAE"],
     "name": "CheckpointLoaderSimple",
     "display_name": "Load Checkpoint",
     "description": "Loads a checkpoint model",
     "category": "loaders"
 }
*/
