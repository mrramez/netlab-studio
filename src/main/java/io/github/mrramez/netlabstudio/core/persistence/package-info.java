/**
 * Saving and loading topologies and learner progress as JSON.
 *
 * <p>Files are read into explicit DTOs and every field is validated. File size and object counts
 * are capped, and unknown schema versions are rejected. Java native serialization is never used,
 * because deserializing untrusted files that way can execute code.
 */
package io.github.mrramez.netlabstudio.core.persistence;
