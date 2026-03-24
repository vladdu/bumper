package com.volvo.bumper.port;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import java.util.List;

public interface ForgePort {
  ForgeType forgeType();

  List<Repository> discoverAll();
}
