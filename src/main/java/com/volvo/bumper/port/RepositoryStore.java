package com.volvo.bumper.port;

import com.volvo.bumper.domain.RepositoryData;

/** Provides atomic read/write access to the full collection of tracked and ignored repositories. */
public interface RepositoryStore {

  /** Returns a consistent snapshot of both tracked and ignored repositories. */
  RepositoryData findAll();

  /** Persists both tracked and ignored repositories atomically. */
  void saveAll(RepositoryData data);
}
